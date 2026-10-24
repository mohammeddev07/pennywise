package com.axel.pennywise.security.admission;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdmissionFilterTest {
  @Test
  void rejectsWithoutWaitingForAnOccupiedSlot() throws Exception {
    var meters = new SimpleMeterRegistry();
    var filter = new AdmissionFilter(1, meters);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var workers = Executors.newSingleThreadExecutor()) {
      var first =
          workers.submit(
              () -> {
                filter.doFilter(
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse(),
                    (req, res) -> {
                      entered.countDown();
                      try {
                        if (!release.await(5, SECONDS))
                          throw new ServletException("test did not release slot");
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ServletException(e);
                      }
                    });
                return null;
              });
      try {
        assertThat(entered.await(2, SECONDS)).isTrue();
        var rejected = new MockHttpServletResponse();
        filter.doFilter(
            new MockHttpServletRequest(),
            rejected,
            (req, res) -> {
              throw new AssertionError(
                  "Rejected work must not reach authentication or database work");
            });
        assertThat(rejected.getStatus()).isEqualTo(503);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("1");
        assertThat(rejected.getContentAsString()).contains("OVERLOADED");
        assertThat(meters.get("http.admission.inflight").gauge().value()).isEqualTo(1);
        assertThat(
                meters.get("http.admission.requests").tag("outcome", "rejected").counter().count())
            .isEqualTo(1);
      } finally {
        release.countDown();
      }
      first.get(2, SECONDS);
    }
    assertThat(meters.get("http.admission.inflight").gauge().value()).isZero();
  }

  @Test
  void releasesSlotWhenDownstreamThrows() throws Exception {
    var meters = new SimpleMeterRegistry();
    var filter = new AdmissionFilter(1, meters);
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse(),
                    (req, res) -> {
                      throw new ServletException("DB failure");
                    }))
        .isInstanceOf(ServletException.class);
    var next = new MockHttpServletResponse();
    filter.doFilter(
        new MockHttpServletRequest(), next, (req, res) -> res.setContentType("text/plain"));
    assertThat(next.getStatus()).isEqualTo(200);
    assertThat(meters.get("http.admission.inflight").gauge().value()).isZero();
    assertThat(meters.get("http.admission.requests").tag("outcome", "admitted").counter().count())
        .isEqualTo(2);
  }

  @Test
  void rejectsInvalidCapacity() {
    assertThatThrownBy(() -> new AdmissionFilter(0, new SimpleMeterRegistry()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
