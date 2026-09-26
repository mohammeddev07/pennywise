package com.axel.pennywise.domain.transaction.query.ai;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Atomic daily quota, safe under concurrent requests and multiple app instances: the UPDATE's
 * {@code WHERE count < ?} means only one of two racing requests can win the last slot, and there is
 * no read-then-write gap for a second instance to land in.
 */
@Component
class AiFilterQuotaService {

  @Value("${app.ai.rate-limit.per-day:100}")
  private int perDay;

  private final JdbcTemplate jdbc;

  AiFilterQuotaService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Reserves one call against today's quota; false means the day's cap is already used up. */
  boolean tryReserve(UUID userId) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Integer reserved =
        jdbc.query(
            """
            INSERT INTO ai_filter_quota (user_id, day, count) VALUES (?, ?, 1)
            ON CONFLICT (user_id, day) DO UPDATE SET count = ai_filter_quota.count + 1
            WHERE ai_filter_quota.count < ?
            RETURNING count
            """,
            rs -> rs.next() ? rs.getInt(1) : null,
            userId,
            today,
            perDay);
    return reserved != null;
  }
}
