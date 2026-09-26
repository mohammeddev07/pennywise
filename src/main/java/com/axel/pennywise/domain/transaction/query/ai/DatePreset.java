package com.axel.pennywise.domain.transaction.query.ai;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Relative date phrases the model may name instead of computing dates itself. The AI only picks one
 * of these; the actual boundaries are resolved here from the book-local reference date, so "last
 * month" is always correct regardless of what the model believes today's date is.
 */
enum DatePreset {
  TODAY,
  YESTERDAY,
  THIS_MONTH,
  LAST_MONTH,
  THIS_YEAR,
  LAST_YEAR;

  record Range(LocalDate start, LocalDate end) {}

  Range resolve(LocalDate today) {
    return switch (this) {
      case TODAY -> new Range(today, today);
      case YESTERDAY -> new Range(today.minusDays(1), today.minusDays(1));
      case THIS_MONTH -> monthRange(YearMonth.from(today));
      case LAST_MONTH -> monthRange(YearMonth.from(today).minusMonths(1));
      case THIS_YEAR -> yearRange(today.getYear());
      case LAST_YEAR -> yearRange(today.getYear() - 1);
    };
  }

  private static Range monthRange(YearMonth ym) {
    return new Range(ym.atDay(1), ym.atEndOfMonth());
  }

  private static Range yearRange(int year) {
    return new Range(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
  }
}
