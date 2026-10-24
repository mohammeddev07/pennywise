package com.axel.pennywise.security.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.login-rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class LoginRateLimitConfig {
  @Bean
  FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilter(
      @Value("${app.login-rate-limit.per-second:0.5}") double perSecond, MeterRegistry meters) {
    var registration = new FilterRegistrationBean<>(new LoginRateLimitFilter(perSecond, meters));
    registration.addUrlPatterns("/v1/auth/login");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
    return registration;
  }
}
