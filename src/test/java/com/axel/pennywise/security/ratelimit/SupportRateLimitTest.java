package com.axel.pennywise.security.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axel.pennywise.exception.ApiException;
import com.axel.pennywise.exception.RateLimitExceededException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerMapping;

class SupportRateLimitTest {
  private final PostgresTokenBucket store = mock(PostgresTokenBucket.class);
  private final RateLimitProperties props =
      new RateLimitProperties(
          false,
          new RateLimitProperties.Bucket(60, 2),
          new RateLimitProperties.Ip(8, 0.1),
          new RateLimitProperties.Global(200, 50),
          new RateLimitProperties.Costs(1, 2, 2, 3, 5, 20, 25, 40, 1),
          new RateLimitProperties.Support(2, 2.0 / 3600, 10, 10.0 / 3600, 20, 20.0 / 86400));
  private final RateLimitInterceptor interceptor = new RateLimitInterceptor(props, store);
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "none"),
            Map.of("sub", "subject-1"));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    request = new MockHttpServletRequest("POST", "/v1/support/contact");
    request.setRemoteAddr("192.0.2.1");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/support/contact");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void supportQuotasApplyWhenGenericLimiterIsDisabled() {
    interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    verify(store).tryConsume(eq("support:u:subject-1"), eq(1.0), eq(2.0), anyDouble());
    verify(store).tryConsume(eq("support:ip:192.0.2.1"), eq(1.0), eq(10.0), anyDouble());
    verify(store).tryConsume(eq("support:global"), eq(1.0), eq(20.0), anyDouble());
  }

  @Test
  void rejectsWhenUserBucketIsEmpty() {
    when(store.tryConsume(eq("support:u:subject-1"), eq(1.0), anyDouble(), anyDouble()))
        .thenReturn(900);
    RateLimitExceededException error =
        assertThrows(
            RateLimitExceededException.class,
            () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    assertEquals("SUPPORT_RATE_LIMITED", error.code());
    assertEquals(900, error.retryAfterSeconds());
  }

  @Test
  void failsClosedWhenLimiterDatabaseIsUnavailable() {
    when(store.tryConsume(eq("support:u:subject-1"), eq(1.0), anyDouble(), anyDouble()))
        .thenThrow(new DataAccessResourceFailureException("database unavailable"));
    ApiException error =
        assertThrows(
            ApiException.class,
            () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    assertEquals("SUPPORT_UNAVAILABLE", error.code());
  }
}
