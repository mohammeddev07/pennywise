package com.axel.pennywise.security.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Calls the {@code consume_tokens} plpgsql function (V9): one atomic round trip per check. */
@Component
class PostgresTokenBucket {

  private final JdbcTemplate jdbc;
  private final String schema;

  PostgresTokenBucket(JdbcTemplate jdbc, @Value("${app.db.schema}") String schema) {
    this.jdbc = jdbc;
    this.schema = schema;
  }

  /** Returns 0 when the cost was taken, otherwise whole seconds until it could be. */
  int tryConsume(String key, double cost, double capacity, double refillPerSecond) {
    return jdbc.queryForObject(
        "SELECT CASE WHEN allowed THEN 0 ELSE greatest(retry_after_seconds, 1) END"
            + " FROM "
            + schema
            + ".consume_tokens(?, ?::numeric, ?::numeric, ?::numeric)",
        Integer.class,
        key,
        cost,
        capacity,
        refillPerSecond);
  }

  /** Buckets idle for a day are long since full, so dropping them changes nothing. */
  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
  void sweepIdle() {
    jdbc.update(
        "DELETE FROM " + schema + ".rate_limit_bucket WHERE updated_at < now() - interval '1 day'");
  }
}
