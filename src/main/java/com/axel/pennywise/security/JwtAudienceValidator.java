package com.axel.pennywise.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

  private static final String ERROR_CODE = "invalid_token";
  private final String requiredAudience;

  public JwtAudienceValidator(String requiredAudience) {
    this.requiredAudience = requiredAudience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    if (requiredAudience == null || requiredAudience.isBlank()) {
      return OAuth2TokenValidatorResult.success();
    }
    List<String> aud = token.getAudience();
    if (aud != null && aud.contains(requiredAudience)) {
      return OAuth2TokenValidatorResult.success();
    }
    OAuth2Error error =
        new OAuth2Error(ERROR_CODE, "JWT is missing required audience: " + requiredAudience, null);
    return OAuth2TokenValidatorResult.failure(error);
  }
}
