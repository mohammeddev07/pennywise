package com.axel.pennywise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Security security, Pagination pagination) {
  public record Security(boolean authEnabled, Jwt jwt) {
    public record Jwt(
        String issuerUri,
        String audience,
        String issuer,
        String localSecret,
        int accessTokenTtlMinutes) {}
  }

  public record Pagination(int defaultLimit, int maxLimit) {}
}
