package com.axel.pennywise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

  @Value("${app.cors.allowed-origins:*}")
  private String allowedOrigins;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(allowedOrigins.split(","))
        .allowedMethods("GET", "POST", "PATCH", "DELETE")
        .allowedHeaders("Authorization", "Content-Type", "If-Match", "Idempotency-Key")
        .maxAge(3600);
  }
}
