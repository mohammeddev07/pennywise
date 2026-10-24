package com.axel.pennywise.security.admission;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.admission.enabled", havingValue = "true", matchIfMissing = true)
public class AdmissionConfig {
  @Bean
  FilterRegistrationBean<AdmissionFilter> admissionFilter(
      @Value("${app.admission.max-in-flight:3}") int maxInFlight, MeterRegistry meters) {
    var registration = new FilterRegistrationBean<>(new AdmissionFilter(maxInFlight, meters));
    registration.addUrlPatterns("/v1/*");
    // After HTTP observation, before authentication, logging and database-backed rate limits.
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
    return registration;
  }
}
