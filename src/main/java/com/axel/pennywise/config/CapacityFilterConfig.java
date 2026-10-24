package com.axel.pennywise.config;

import com.axel.pennywise.security.admission.AdmissionFilter;
import com.axel.pennywise.security.ratelimit.LoginRateLimitFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CompositeFilter;

@Configuration(proxyBeanMethods = false)
public class CapacityFilterConfig {
  @Bean
  FilterRegistrationBean<CompositeFilter> capacityFilters(
      @Value("${app.admission.enabled:true}") boolean admissionEnabled,
      @Value("${app.admission.max-in-flight:3}") int maxInFlight,
      @Value("${app.login-rate-limit.enabled:true}") boolean loginLimitEnabled,
      @Value("${app.login-rate-limit.per-second:0.5}") double loginsPerSecond,
      MeterRegistry meters) {
    var filters = new ArrayList<Filter>();
    if (loginLimitEnabled) filters.add(new LoginRateLimitFilter(loginsPerSecond, meters));
    if (admissionEnabled) filters.add(new AdmissionFilter(maxInFlight, meters));
    var composite = new CompositeFilter();
    composite.setFilters(filters);
    var registration = new FilterRegistrationBean<>(composite);
    registration.addUrlPatterns("/v1/*");
    // Reject before HTTP observation, authentication, request logging and DB-backed policy.
    // One registration keeps the two controls ordered ahead of observation's MIN_VALUE + 1.
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.setEnabled(!filters.isEmpty());
    return registration;
  }
}
