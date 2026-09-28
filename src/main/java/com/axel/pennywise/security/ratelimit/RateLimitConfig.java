package com.axel.pennywise.security.ratelimit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfig implements WebMvcConfigurer {

  // ObjectProvider: web-layer test slices load this config without the DB-backed store. The
  // interceptor is deliberately not a bean: @WebMvcTest would pick it up and demand the store.
  private final RateLimitProperties props;
  private final ObjectProvider<PostgresTokenBucket> store;

  RateLimitConfig(RateLimitProperties props, ObjectProvider<PostgresTokenBucket> store) {
    this.props = props;
    this.store = store;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    store.ifAvailable(
        s ->
            registry
                .addInterceptor(new RateLimitInterceptor(props, s))
                .excludePathPatterns(
                    "/health", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**"));
  }
}
