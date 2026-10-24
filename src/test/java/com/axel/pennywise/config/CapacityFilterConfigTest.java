package com.axel.pennywise.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CapacityFilterConfigTest {
  @Test
  void deniedLoginsNeverEnterLaterFiltersOrSpendAnAdmissionPermit() throws Exception {
    var meters = new SimpleMeterRegistry();
    var filter = new CapacityFilterConfig().capacityFilters(true, 3, true, 0.5, meters).getFilter();
    filter.init(new MockFilterConfig("capacityFilters"));
    var downstream = new AtomicInteger();
    for (int i = 0; i < 2; i++) {
      var request = new MockHttpServletRequest("POST", "/v1/auth/login");
      request.setRemoteAddr("198.18.0." + i);
      var response = new MockHttpServletResponse();
      filter.doFilter(request, response, (req, res) -> downstream.incrementAndGet());
      assertThat(response.getStatus()).isEqualTo(i == 0 ? 200 : 429);
    }
    assertThat(downstream.get()).isEqualTo(1);
    assertThat(meters.get("http.admission.requests").tag("outcome", "admitted").counter().count())
        .isEqualTo(1);
    assertThat(meters.get("http.admission.inflight").gauge().value()).isZero();
  }

  @Test
  void disabledControlsRegisterNoFilterOrCapacityMeters() {
    var meters = new SimpleMeterRegistry();
    var registration = new CapacityFilterConfig().capacityFilters(false, 3, false, 0.5, meters);
    assertThat(registration.isEnabled()).isFalse();
    assertThat(meters.getMeters()).isEmpty();
  }
}
