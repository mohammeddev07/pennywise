package com.axel.pennywise.domain.auth;

import com.axel.pennywise.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies a Google ID token sent by the mobile app: signature against Google's published keys,
 * issuer, audience (must be one of our own OAuth client IDs), expiry, and a verified email. Not a
 * {@code JwtDecoder} bean on purpose - the resource server already has one for Pennywise tokens.
 */
@Component
public class GoogleTokenVerifier {

  public record GoogleIdentity(String sub, String email) {}

  private static final Set<String> ISSUERS =
      Set.of("https://accounts.google.com", "accounts.google.com");

  private final List<String> clientIds;
  private final NimbusJwtDecoder decoder;

  @Autowired
  public GoogleTokenVerifier(
      @Value("${app.google.client-ids:}") List<String> clientIds,
      @Value("${app.google.jwks-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwksUri) {
    this(
        clientIds,
        NimbusJwtDecoder.withJwkSetUri(jwksUri).jwsAlgorithm(SignatureAlgorithm.RS256).build());
  }

  GoogleTokenVerifier(List<String> clientIds, NimbusJwtDecoder decoder) {
    this.clientIds = clientIds.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    this.decoder = decoder;
    // Google may send the bare "accounts.google.com" as issuer; Spring's default converter would
    // reject it as a malformed URL, so keep iss as a plain string and compare it ourselves.
    decoder.setClaimSetConverter(MappedJwtClaimSetConverter.withDefaults(Map.of("iss", v -> v)));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(), issuerValidator(), audienceValidator()));
  }

  public GoogleIdentity verify(String idToken) {
    if (clientIds.isEmpty()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "GOOGLE_SIGN_IN_NOT_CONFIGURED",
          "Google sign-in is not enabled on this server");
    }
    Jwt jwt;
    try {
      jwt = decoder.decode(idToken);
    } catch (JwtException e) {
      throw invalid();
    }
    String email = jwt.getClaimAsString("email");
    Object emailVerified = jwt.getClaim("email_verified"); // Boolean, or "true" in older tokens
    boolean verified = "true".equals(String.valueOf(emailVerified));
    if (!verified || email == null || email.isBlank() || jwt.getSubject() == null) {
      throw invalid();
    }
    return new GoogleIdentity(jwt.getSubject(), email);
  }

  private OAuth2TokenValidator<Jwt> issuerValidator() {
    return jwt ->
        jwt.getClaimAsString("iss") != null && ISSUERS.contains(jwt.getClaimAsString("iss"))
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "bad issuer", null));
  }

  private OAuth2TokenValidator<Jwt> audienceValidator() {
    return jwt ->
        jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(clientIds::contains)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "bad audience", null));
  }

  private ApiException invalid() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "GOOGLE_TOKEN_INVALID", "Google sign-in token is invalid");
  }
}
