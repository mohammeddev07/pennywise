package com.axel.pennywise.security.ratelimit;

/** In-memory token bucket for the global limit. Single instance only, resets on restart. */
class LocalTokenBucket {

  private final double capacity;
  private final double refillPerSecond;
  private double tokens;
  private long lastNanos;

  LocalTokenBucket(double capacity, double refillPerSecond) {
    this(capacity, refillPerSecond, System.nanoTime());
  }

  LocalTokenBucket(double capacity, double refillPerSecond, long startNanos) {
    this.capacity = capacity;
    this.refillPerSecond = refillPerSecond;
    this.tokens = capacity;
    this.lastNanos = startNanos;
  }

  /** Returns 0 when the cost was taken, otherwise whole seconds until it could be. */
  synchronized int tryConsume(double cost) {
    return tryConsume(cost, System.nanoTime());
  }

  synchronized int tryConsume(double cost, long nowNanos) {
    double c = Math.min(cost, capacity);
    tokens = Math.min(capacity, tokens + (nowNanos - lastNanos) / 1e9 * refillPerSecond);
    lastNanos = nowNanos;
    if (tokens >= c) {
      tokens -= c;
      return 0;
    }
    return (int) Math.ceil((c - tokens) / refillPerSecond);
  }
}
