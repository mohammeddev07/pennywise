package com.axel.pennywise.security.ratelimit;

import com.axel.pennywise.exception.ApiException;
import com.axel.pennywise.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Runs after authentication, so it can key on the JWT subject with no DB lookup. Order: the
 * caller's own bucket first (a flooding caller must not drain the global bucket with rejected
 * requests), then the global one. Throws {@link RateLimitExceededException}, which
 * GlobalExceptionHandler turns into a 429 with Retry-After.
 */
@Slf4j
class RateLimitInterceptor implements HandlerInterceptor {

  private static final String BOOK = "/v1/books/{bookId}";
  private static final String TX = BOOK + "/transactions";
  private static final String SUPPORT_CONTACT = "/v1/support/contact";

  private final RateLimitProperties props;
  private final PostgresTokenBucket store;
  private final LocalTokenBucket global;

  RateLimitInterceptor(RateLimitProperties props, PostgresTokenBucket store) {
    this.props = props;
    this.store = store;
    this.global = new LocalTokenBucket(props.global().capacity(), props.global().refillPerSecond());
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;

    String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    boolean support =
        SUPPORT_CONTACT.equals(pattern) && HttpMethod.POST.matches(request.getMethod());
    if (!props.enabled()) {
      if (support) consumeSupport(request);
      return true;
    }
    boolean auth = pattern != null && pattern.startsWith("/v1/auth/");
    int cost = cost(request.getMethod(), pattern);

    int retryAfter;
    try {
      retryAfter = consumeCaller(request, auth, cost);
    } catch (DataAccessException e) {
      // Availability beats throttling: a broken limiter must not take the API down.
      log.warn("Rate limit check failed, allowing request: {}", e.getMessage());
      retryAfter = 0;
    }
    if (retryAfter == 0) retryAfter = global.tryConsume(cost);

    if (retryAfter > 0) {
      throw new RateLimitExceededException(
          "RATE_LIMITED",
          "Too many requests. Try again in " + retryAfter + " seconds.",
          retryAfter);
    }
    if (support) consumeSupport(request);
    return true;
  }

  private void consumeSupport(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwt)) {
      // This route must never send mail when authentication has been disabled locally.
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
    }

    var limits = props.support();
    try {
      int retryAfter =
          store.tryConsume(
              "support:u:" + jwt.getToken().getSubject(),
              1,
              limits.userCapacity(),
              limits.userRefillPerSecond());
      if (retryAfter == 0) {
        retryAfter =
            store.tryConsume(
                "support:ip:" + request.getRemoteAddr(),
                1,
                limits.ipCapacity(),
                limits.ipRefillPerSecond());
      }
      if (retryAfter == 0) {
        retryAfter =
            store.tryConsume(
                "support:global", 1, limits.globalCapacity(), limits.globalRefillPerSecond());
      }
      if (retryAfter > 0) {
        throw new RateLimitExceededException(
            "SUPPORT_RATE_LIMITED", "Too many support messages. Try again later.", retryAfter);
      }
    } catch (DataAccessException e) {
      log.warn("Support rate limit check failed: {}", e.getClass().getSimpleName());
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "SUPPORT_UNAVAILABLE",
          "Support is temporarily unavailable. Please try again later.");
    }
  }

  private int consumeCaller(HttpServletRequest request, boolean auth, int cost) {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a instanceof JwtAuthenticationToken jwt) {
      var b = props.user();
      return store.tryConsume(
          "u:" + jwt.getToken().getSubject(), cost, b.capacity(), b.refillPerSecond());
    }
    if (auth) {
      var b = props.ip();
      return store.tryConsume(
          "auth-ip:" + request.getRemoteAddr(), cost, b.capacity(), b.refillPerSecond());
    }
    // Auth disabled (local/test): no principal, so fall back to the caller's address.
    var b = props.user();
    return store.tryConsume(
        "ip:" + request.getRemoteAddr(), cost, b.capacity(), b.refillPerSecond());
  }

  int cost(String method, String pattern) {
    var c = props.costs();
    if (pattern == null) return c.read();
    if (pattern.startsWith("/v1/auth/")) return c.auth();
    String key = method + " " + pattern;
    Map<String, Integer> special =
        Map.of(
            "POST " + TX + "/import", c.importFile(),
            "GET " + TX + "/export", c.export(),
            "POST " + TX + "/export/query", c.export(),
            "POST " + BOOK + "/filter-proposals", c.aiFilter(),
            "GET " + BOOK + "/summary/range", c.heavyAggregate(),
            "POST " + TX + "/analyze", c.heavyAggregate(),
            "POST " + TX + "/search", c.search(),
            "GET " + BOOK + "/summary/monthly", c.aggregate(),
            "GET " + BOOK + "/budgets", c.aggregate(),
            "GET " + TX, c.aggregate());
    Integer cost = special.get(key);
    if (cost != null) return cost;
    if ("GET /v1/books".equals(key)) return c.aggregate(); // fresh per-book balances
    return HttpMethod.GET.matches(method) ? c.read() : c.write();
  }
}
