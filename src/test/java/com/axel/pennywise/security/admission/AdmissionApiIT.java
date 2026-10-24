package com.axel.pennywise.security.admission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axel.pennywise.domain.auth.AuthService;
import com.axel.pennywise.domain.transaction.query.AbstractPostgresIT;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    properties = {
      "app.security.auth-enabled=true",
      "app.admission.enabled=true",
      "app.admission.max-in-flight=1",
      "app.login-rate-limit.enabled=true",
      "management.endpoint.health.probes.enabled=true",
      "app.rate-limit.enabled=false"
    })
@AutoConfigureMockMvc
@Import(AdmissionApiIT.BlockingController.class)
class AdmissionApiIT extends AbstractPostgresIT {
  @Autowired MockMvc mvc;
  @Autowired BlockingController blocking;
  @MockBean AuthService auth;

  @RestController
  static class BlockingController {
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    @GetMapping("/v1/_capacity-test")
    String waitForRelease() throws InterruptedException {
      entered.countDown();
      if (!release.await(5, TimeUnit.SECONDS))
        throw new IllegalStateException("test did not release");
      return "ok";
    }
  }

  @Test
  void overloadIsRejectedBeforeAuthenticationAndHealthRemainsAvailable() throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      var first =
          executor.submit(
              () ->
                  mvc.perform(get("/v1/_capacity-test").with(jwt()))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      try {
        assertThat(blocking.entered.await(2, TimeUnit.SECONDS)).isTrue();
        var response = mvc.perform(get("/v1/_capacity-test")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
      } finally {
        blocking.release.countDown();
      }
      assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(200);
    }
  }

  @Test
  void distributedLoginAttemptsAreLimitedBeforeTheController() throws Exception {
    for (int i = 0; i < 5; i++) {
      final int address = i;
      mvc.perform(
              post("/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"perf@lab.invalid\",\"password\":\"password123\"}")
                  .with(
                      request -> {
                        request.setRemoteAddr("198.18.0." + address);
                        return request;
                      }))
          .andExpect(status().is(i == 0 ? 200 : 429));
    }
    verify(auth, times(1)).login(anyString(), anyString());
  }
}
