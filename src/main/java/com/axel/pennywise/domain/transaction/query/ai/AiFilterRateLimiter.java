package com.axel.pennywise.domain.transaction.query.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-minute burst guard, checked before {@link AiFilterQuotaService}'s daily cap.
 *
 * <p>ponytail: in-memory sliding-minute counter, resets on restart and is per-instance (a user
 * hitting two app instances could get 2x this limit for one minute). {@link AiFilterQuotaService}'s
 * DB-backed daily cap is the real, multi-instance-safe backstop; this just smooths local bursts
 * cheaply. Upgrade to a shared store (e.g. Redis) if the app ever runs enough instances for that
 * gap to matter.
 */
@Component
class AiFilterRateLimiter {

  @Value("${app.ai.rate-limit.per-minute:10}")
  private int perMinute;

  private final Cache<String, AtomicInteger> counts =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(70)).maximumSize(10_000).build();

  boolean tryReserve(UUID userId) {
    long minuteBucket = Instant.now().getEpochSecond() / 60;
    String key = userId + ":" + minuteBucket;
    int count = counts.get(key, k -> new AtomicInteger()).incrementAndGet();
    return count <= perMinute;
  }
}
