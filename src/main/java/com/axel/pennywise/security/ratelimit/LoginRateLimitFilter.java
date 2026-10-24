package com.axel.pennywise.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.LongSupplier;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/** Protects password hashing and the database-backed limiter on the single-instance free plan. */
public final class LoginRateLimitFilter extends OncePerRequestFilter {
  private static final byte[] LIMITED =
      ("{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many login attempts; retry later\","
              + "\"details\":[],\"requestId\":null}}")
          .getBytes(StandardCharsets.UTF_8);
  private final Cache<String, LocalTokenBucket> callers;
  private final LocalTokenBucket global;
  private final LongSupplier nanos;
  private final Counter allowed;
  private final Counter rejected;

  public LoginRateLimitFilter(double perSecond, MeterRegistry meters) {
    this(perSecond, meters, System::nanoTime);
  }

  @Override
  protected String getAlreadyFilteredAttributeName() {
    return LoginRateLimitFilter.class.getName() + ALREADY_FILTERED_SUFFIX;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod())) return true;
    String path = request.getServletPath();
    if (path.isEmpty()) path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    return !"/v1/auth/login".equals(path);
  }

  LoginRateLimitFilter(double perSecond, MeterRegistry meters, LongSupplier nanos) {
    if (!Double.isFinite(perSecond) || perSecond <= 0) {
      throw new IllegalArgumentException("Login refill rate must be finite and positive");
    }
    this.nanos = nanos;
    global = new LocalTokenBucket(1, perSecond, nanos.getAsLong());
    callers =
        Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .ticker(nanos::getAsLong)
            .build();
    allowed = meters.counter("http.login.admission.requests", "outcome", "allowed");
    rejected = meters.counter("http.login.admission.requests", "outcome", "rejected");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long now = nanos.getAsLong();
    int retry =
        callers
            .get(request.getRemoteAddr(), ip -> new LocalTokenBucket(2, 0.1, now))
            .tryConsume(1, now);
    if (retry == 0) retry = global.tryConsume(1, now);
    if (retry == 0) {
      allowed.increment();
      chain.doFilter(request, response);
    } else {
      rejected.increment();
      response.setStatus(429);
      response.setHeader("Retry-After", String.valueOf(retry));
      response.setHeader("Cache-Control", "no-store");
      response.setContentType("application/json");
      response.setContentLength(LIMITED.length);
      response.getOutputStream().write(LIMITED);
    }
  }
}
