package com.axel.pennywise.domain.common;

/**
 * Numeric bounds shared with the mobile client (see {@code shared/utils/formatCurrency.ts}).
 *
 * <p>Amounts are integer minor units in a Java {@code long}, but every client reads them as a
 * JavaScript {@code number}, which is only exact up to 2^53 - 1. A single transaction is capped
 * well below that so per-book aggregates (sums over many rows) stay exactly representable on both
 * sides; the aggregate cap is the JS safe-integer limit and is enforced where sums are produced.
 */
public final class MoneyLimits {

  /** Largest {@code amountMinor} accepted on a single transaction: 15 decimal digits. */
  public static final long MAX_TRANSACTION_AMOUNT_MINOR = 999_999_999_999_999L;

  /** Largest total any aggregate may report: {@code Number.MAX_SAFE_INTEGER} (2^53 - 1). */
  public static final long MAX_AGGREGATE_AMOUNT_MINOR = 9_007_199_254_740_991L;

  private MoneyLimits() {}
}
