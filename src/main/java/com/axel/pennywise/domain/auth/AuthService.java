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

  private final UserRepository userRepo;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;

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

    UserEntity user = new UserEntity();
    user.setAuthSubject(subject);
    user.setEmail(normalizedEmail);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setDefaultCurrencyCode(normalizeCurrency(defaultCurrencyCode));

    return tokenResponse(userRepo.save(user));
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
