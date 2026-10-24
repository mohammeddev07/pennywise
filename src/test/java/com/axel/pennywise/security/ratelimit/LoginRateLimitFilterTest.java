package com.axel.pennywise.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginRateLimitFilterTest {
  @Test
  void distributedAddressesCannotBypassGlobalBudgetAndNoRejectedRequestReachesDb()
      throws Exception {
    var time = new AtomicLong();
    var meters = new SimpleMeterRegistry();
    var filter = new LoginRateLimitFilter(0.5, meters, time::get);
    var entered = new AtomicInteger();
    for (int i = 0; i < 100; i++) {
      var request = new MockHttpServletRequest("POST", "/v1/auth/login");
      request.setRemoteAddr("198.18.0." + i);
      var response = new MockHttpServletResponse();
      filter.doFilter(request, response, (req, res) -> entered.incrementAndGet());
      if (i > 0) {
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("2");
      }
    }
    assertThat(entered.get()).isEqualTo(1);
    assertThat(
            meters
                .get("http.login.admission.requests")
                .tag("outcome", "rejected")
                .counter()
                .count())
        .isEqualTo(99);
    time.set(2_000_000_000L);
    var next = new MockHttpServletRequest("POST", "/v1/auth/login");
    next.setRemoteAddr("198.18.1.1");
    filter.doFilter(next, new MockHttpServletResponse(), (req, res) -> entered.incrementAndGet());
    assertThat(entered.get()).isEqualTo(2);
  }

  @Test
  void oneAddressCannotConsumeTheGlobalBudgetContinuously() throws Exception {
    var time = new AtomicLong();
    var filter = new LoginRateLimitFilter(10, new SimpleMeterRegistry(), time::get);
    for (int i = 0; i < 2; i++) {
      var request = new MockHttpServletRequest("POST", "/v1/auth/login");
      request.setRemoteAddr("198.18.0.1");
      filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
      time.addAndGet(1_000_000_000L);
    }
    var request = new MockHttpServletRequest("POST", "/v1/auth/login");
    request.setRemoteAddr("198.18.0.1");
    var response = new MockHttpServletResponse();
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          throw new AssertionError("must reject");
        });
    assertThat(response.getStatus()).isEqualTo(429);
  }

  @Test
  void optionsDoesNotConsumeTheBudget() throws Exception {
    var filter = new LoginRateLimitFilter(0.5, new SimpleMeterRegistry());
    var response = new MockHttpServletResponse();
    filter.doFilter(
        new MockHttpServletRequest("OPTIONS", "/v1/auth/login"),
        response,
        (req, res) -> res.setContentType("text/plain"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void invalidBudgetsFailConfiguration() {
    assertThatThrownBy(() -> new LoginRateLimitFilter(0, new SimpleMeterRegistry()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LoginRateLimitFilter(Double.NaN, new SimpleMeterRegistry()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
