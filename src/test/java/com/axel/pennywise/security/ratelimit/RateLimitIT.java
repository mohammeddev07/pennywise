package com.axel.pennywise.security.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axel.pennywise.domain.transaction.query.AbstractPostgresIT;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "app.security.auth-enabled=true",
      "app.rate-limit.enabled=true",
      "app.rate-limit.user.capacity=3",
      "app.rate-limit.user.refill-per-second=0.001",
      "app.rate-limit.ip.capacity=2",
      "app.rate-limit.ip.refill-per-second=0.001"
    })
class RateLimitIT extends AbstractPostgresIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private boolean consume(
      String key, double cost, double capacity, double perSec, OffsetDateTime now) {
    return jdbc.queryForObject(
        "SELECT allowed FROM expense_tracker.consume_tokens(?, ?::numeric, ?::numeric, ?::numeric,"
            + " ?::timestamptz)",
        Boolean.class,
        key,
        cost,
        capacity,
        perSec,
        now);
  }

  @Test
  void burstAcrossAWindowBoundaryIsNotDoubled() {
    // "100 per minute": a fixed window would let 100 through at 0:59 and 100 more at 1:00.
    String key = "test:" + UUID.randomUUID();
    OffsetDateTime t = OffsetDateTime.parse("2026-01-01T00:00:59Z");
    double perSec = 100.0 / 60;

    int first = 0;
    for (int i = 0; i < 200; i++) if (consume(key, 1, 100, perSec, t)) first++;
    int second = 0;
    for (int i = 0; i < 200; i++) if (consume(key, 1, 100, perSec, t.plusSeconds(1))) second++;

    assertEquals(100, first);
    assertEquals(1, second); // one second of refill = 1.67 tokens
  }

  @Test
  void concurrentCallersOnOneKeyNeverSpendTheSameToken() throws Exception {
    String key = "test:" + UUID.randomUUID();
    ExecutorService pool = Executors.newFixedThreadPool(16);
    try {
      List<Callable<Boolean>> calls = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        calls.add(() -> consume(key, 1, 20, 0.0001, OffsetDateTime.now()));
      }
      int allowed = 0;
      for (Future<Boolean> f : pool.invokeAll(calls)) if (f.get()) allowed++;
      assertEquals(20, allowed);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void userIsThrottledWith429RetryAfterAndIndependentPerUser() throws Exception {
    String a = "user-a-" + UUID.randomUUID();
    String b = "user-b-" + UUID.randomUUID();
    for (int i = 0; i < 3; i++) {
      mvc.perform(get("/v1/me").with(jwt().jwt(j -> j.subject(a)))).andExpect(status().isOk());
    }
    mvc.perform(get("/v1/me").with(jwt().jwt(j -> j.subject(a))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    mvc.perform(get("/v1/me").with(jwt().jwt(j -> j.subject(b)))).andExpect(status().isOk());
  }

  @Test
  void authEndpointsAreThrottledPerIpBeforeAnyPasswordCheck() throws Exception {
    String body = "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}";
    for (int i = 0; i < 2; i++) {
      mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }
}
