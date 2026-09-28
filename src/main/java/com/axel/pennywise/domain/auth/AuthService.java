package com.axel.pennywise.domain.auth;

import com.axel.pennywise.api.dto.auth.AuthResponse;
import com.axel.pennywise.api.dto.user.MeResponse;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserRepository;
import com.axel.pennywise.exception.ApiException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String BEARER = "Bearer";
  private static final String LOCAL_PREFIX = "local:";
  private static final String GOOGLE_PREFIX = "google:";

  private final UserRepository userRepo;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final GoogleTokenVerifier googleVerifier;

  @Value("${app.security.jwt.issuer:pennywise}")
  private String issuer;

  @Value("${app.security.jwt.audience:}")
  private String audience;

  @Value("${app.security.jwt.access-token-ttl-minutes:60}")
  private long tokenTtlMinutes;

  @Transactional
  public AuthResponse signup(String email, String password, String defaultCurrencyCode) {
    String normalizedEmail = normalizeEmail(email);
    String subject = localSubject(normalizedEmail);

    if (userRepo.existsByAuthSubjectAndDeletedAtIsNull(subject)) {
      throw new ApiException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }

    // Google-created users have a different auth_subject but the same email; keep one account
    // per email so a later local signup can't shadow a Google user.
    if (userRepo.existsActiveByEmail(normalizedEmail)) {
      throw new ApiException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }

    UserEntity user = new UserEntity();
    user.setAuthSubject(subject);
    user.setEmail(normalizedEmail);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setDefaultCurrencyCode(normalizeCurrency(defaultCurrencyCode));

    try {
      return tokenResponse(userRepo.saveAndFlush(user));
    } catch (DataIntegrityViolationException e) {
      // Lost a race with a concurrent signup for the same email (unique index on lower(email)).
      throw new ApiException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }
  }

  @Transactional(readOnly = true)
  public AuthResponse login(String email, String password) {
    String normalizedEmail = normalizeEmail(email);
    UserEntity user =
        userRepo
            .findByAuthSubjectAndDeletedAtIsNull(localSubject(normalizedEmail))
            .filter(u -> u.getPasswordHash() != null)
            .orElseThrow(() -> invalidCredentials());

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw invalidCredentials();
    }

    return tokenResponse(user);
  }

  /**
   * Log in (or first-time sign up) with a Google ID token. An email that already belongs to another
   * account is never merged automatically: local signup doesn't verify email ownership, so merging
   * by email would let someone who pre-registered a victim's address take over their Google login.
   * The owner must log in with their password and link Google explicitly.
   */
  @Transactional
  public AuthResponse googleLogin(String idToken) {
    GoogleTokenVerifier.GoogleIdentity google = googleVerifier.verify(idToken);

    var linked = userRepo.findByGoogleSubAndDeletedAtIsNull(google.sub());
    if (linked.isPresent()) {
      return tokenResponse(linked.get());
    }

    String email = normalizeEmail(google.email());
    if (userRepo.existsActiveByEmail(email)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "GOOGLE_ACCOUNT_LINK_REQUIRED",
          "An account with this email already exists. Log in with your password, then link"
              + " Google.");
    }

    UserEntity user = new UserEntity();
    user.setAuthSubject(GOOGLE_PREFIX + google.sub());
    user.setEmail(email);
    user.setGoogleSub(google.sub());
    try {
      return tokenResponse(userRepo.saveAndFlush(user));
    } catch (DataIntegrityViolationException e) {
      // Lost a race: the same email (or Google account) was just created by another request.
      throw new ApiException(
          HttpStatus.CONFLICT,
          "GOOGLE_ACCOUNT_LINK_REQUIRED",
          "An account with this email already exists. Log in with your password, then link"
              + " Google.");
    }
  }

  /** Attach a verified Google account to the already-authenticated user. Idempotent. */
  @Transactional
  public MeResponse linkGoogle(UserEntity caller, String idToken) {
    GoogleTokenVerifier.GoogleIdentity google = googleVerifier.verify(idToken);
    UserEntity user =
        userRepo
            .lockActiveById(caller.getId())
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized"));

    boolean takenByOther =
        userRepo
            .findByGoogleSubAndDeletedAtIsNull(google.sub())
            .filter(other -> !other.getId().equals(user.getId()))
            .isPresent();
    boolean userHasOther = user.getGoogleSub() != null && !user.getGoogleSub().equals(google.sub());
    if (takenByOther || userHasOther) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "GOOGLE_ACCOUNT_ALREADY_LINKED",
          "This Google account or this Pennywise account is already linked to another one");
    }

    user.setGoogleSub(google.sub());
    return toMeResponse(userRepo.save(user));
  }

  public MeResponse toMeResponse(UserEntity user) {
    return new MeResponse(
        user.getId(), user.getEmail(), user.getDefaultCurrencyCode(), user.getCreatedAt());
  }

  private AuthResponse tokenResponse(UserEntity user) {
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(tokenTtlMinutes, ChronoUnit.MINUTES);

    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(user.getAuthSubject())
            .claim("email", user.getEmail())
            .claim("user_id", user.getId().toString());

    if (audience != null && !audience.isBlank()) {
      claims.audience(java.util.List.of(audience));
    }

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token =
        jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    long expiresIn = Math.max(0, expiresAt.getEpochSecond() - issuedAt.getEpochSecond());

    return new AuthResponse(token, BEARER, expiresIn, toMeResponse(user));
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeCurrency(String defaultCurrencyCode) {
    if (defaultCurrencyCode == null || defaultCurrencyCode.isBlank()) {
      return null;
    }
    return defaultCurrencyCode.trim().toUpperCase(Locale.ROOT);
  }

  private String localSubject(String email) {
    return LOCAL_PREFIX + email;
  }

  private ApiException invalidCredentials() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
  }
}
