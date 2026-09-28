package com.axel.pennywise.security.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LocalTokenBucketTest {

  private static final long SECOND = 1_000_000_000L;

  @Test
  void spendsCapacityThenRejectsWithRetryAfterAndRefills() {
    var bucket = new LocalTokenBucket(3, 1, 0);
    assertEquals(0, bucket.tryConsume(2, 0));
    assertEquals(0, bucket.tryConsume(1, 0));
    assertEquals(2, bucket.tryConsume(2, 0)); // empty: 2 tokens at 1/s
    assertEquals(0, bucket.tryConsume(2, 2 * SECOND)); // refilled
  }

  @Test
  void costAboveCapacityIsCappedSoItIsNeverBlockedForever() {
    assertEquals(0, new LocalTokenBucket(3, 1, 0).tryConsume(99, 0));
  }
}
