package com.axel.pennywise.security.admission;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds database-capable work without putting overload into another waiting queue. */
public final class AdmissionFilter extends OncePerRequestFilter {
  private static final byte[] BUSY =
      ("{\"error\":{\"code\":\"OVERLOADED\",\"message\":\"Server is busy; retry later\","
              + "\"details\":[],\"requestId\":null}}")
          .getBytes(StandardCharsets.UTF_8);

  private final Semaphore slots;
  private final Counter admitted;
  private final Counter rejected;

  public AdmissionFilter(int maxInFlight, MeterRegistry meters) {
    if (maxInFlight < 1) throw new IllegalArgumentException("maxInFlight must be positive");
    slots = new Semaphore(maxInFlight);
    admitted = meters.counter("http.admission.requests", "outcome", "admitted");
    rejected = meters.counter("http.admission.requests", "outcome", "rejected");
    meters.gauge("http.admission.inflight", slots, s -> maxInFlight - s.availablePermits());
  }

  @Override
  protected String getAlreadyFilteredAttributeName() {
    // CompositeFilter initializes its delegates with the same registration name.
    return AdmissionFilter.class.getName() + ALREADY_FILTERED_SUFFIX;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!slots.tryAcquire()) {
      rejected.increment();
      response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      response.setHeader("Retry-After", "1");
      response.setHeader("Cache-Control", "no-store");
      response.setContentType("application/json");
      response.setContentLength(BUSY.length);
      response.getOutputStream().write(BUSY);
      return;
    }
    admitted.increment();
    try {
      chain.doFilter(request, response);
    } finally {
      slots.release();
    }
  }
}
