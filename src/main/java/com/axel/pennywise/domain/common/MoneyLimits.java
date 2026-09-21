package com.axel.pennywise.domain.common;

import java.util.Currency;

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

  /**
   * Decimal places of a book currency's minor unit: 2 for USD (cents), 0 for JPY (whole yen), the
   * ISO 4217 value otherwise. Amounts are stored as integer minor units, so anything that converts
   * them to or from a decimal (spreadsheet export/import) must scale by this, not by a fixed 2. An
   * unknown code falls back to 2.
   */
  public static int minorUnitDigits(String currencyCode) {
    try {
      int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
      return digits < 0 ? 2 : digits;
    } catch (IllegalArgumentException | NullPointerException e) {
      return 2;
    }
  }

  private MoneyLimits() {}
}
