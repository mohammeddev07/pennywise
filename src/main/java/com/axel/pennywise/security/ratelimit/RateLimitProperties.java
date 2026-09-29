package com.axel.pennywise.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Every rate-limit number lives here (override via {@code app.rate-limit.*}). Sized for a personal
 * finance app on Render's free tier (single instance, 3 DB connections), not a public API.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue Bucket user,
    @DefaultValue Ip ip,
    @DefaultValue Global global,
    @DefaultValue Costs costs,
    @DefaultValue Support support) {

  /** Per authenticated user, in Postgres. A mobile screen load fires ~5 GETs at once. */
  public record Bucket(
      @DefaultValue("60") double capacity, @DefaultValue("2") double refillPerSecond) {}

  /** Per client IP for /v1/auth/**; ~6 attempts a minute sustained, absorbs typos. */
  public record Ip(
      @DefaultValue("8") double capacity, @DefaultValue("0.1") double refillPerSecond) {}

  /** Whole backend, in memory (single instance); user buckets are checked first. */
  public record Global(
      @DefaultValue("200") double capacity, @DefaultValue("50") double refillPerSecond) {}

  /** Relative cost per request type, from the DB/CPU work each one does. */
  public record Costs(
      @DefaultValue("1") int read,
      @DefaultValue("2") int aggregate,
      @DefaultValue("2") int write,
      @DefaultValue("3") int search,
      @DefaultValue("5") int heavyAggregate,
      @DefaultValue("20") int aiFilter,
      @DefaultValue("25") int export,
      @DefaultValue("40") int importFile,
      @DefaultValue("1") int auth) {}

  /** Dedicated, DB-backed support quotas. These apply even when generic limiting is disabled. */
  public record Support(
      @DefaultValue("2") double userCapacity,
      @DefaultValue("0.0005555555555555556") double userRefillPerSecond,
      @DefaultValue("10") double ipCapacity,
      @DefaultValue("0.002777777777777778") double ipRefillPerSecond,
      @DefaultValue("20") double globalCapacity,
      @DefaultValue("0.0002314814814814815") double globalRefillPerSecond) {}
}
