package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.category.CategoryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates over ALL rows matching {@code effectiveFilter} (the request filter AND {@code
 * occurredOn BETWEEN window}). Amounts are positive minor units; {@code netMinor = income -
 * expense}. Opening balance is not part of any figure here.
 */
public record TransactionAnalysisResponse(
    UUID bookId,
    String currencyCode,
    String bucket,
    Window window,
    Map<String, Object> effectiveFilter,
    String queryFingerprint,
    long matchedCount,
    long incomeTotalMinor,
    long expenseTotalMinor,
    long netMinor,
    List<CategoryTotalItem> categories,
    List<BucketItem> buckets,
    List<CategoryBucketItem> categoryBuckets,
    TransactionResponse largestExpense,
    MonthlyBudgets monthlyBudgets) {

  public record Window(LocalDate startDate, LocalDate endDate) {}

  /**
   * {@code percentOfExpense}: share of the FILTERED expense total; null for income or a zero total.
   */
  public record CategoryTotalItem(
      UUID categoryId,
      String categoryName,
      CategoryType type,
      long totalMinor,
      long count,
      BigDecimal percentOfExpense) {}

  /** {@code start}/{@code end} are clipped to the window; {@code partial} marks clipped periods. */
  public record BucketItem(
      String key,
      LocalDate start,
      LocalDate end,
      boolean partial,
      long incomeTotalMinor,
      long expenseTotalMinor,
      long netMinor,
      long count) {}

  /** Dense: one cell per (matching category, type) x bucket, zeros included. */
  public record CategoryBucketItem(
      UUID categoryId, CategoryType type, String bucketKey, long totalMinor, long count) {}

  /** Present only when the window is exactly one full calendar month. */
  public record MonthlyBudgets(String month, String label, List<BudgetItem> items) {}

  public record BudgetItem(UUID categoryId, String categoryName, long amountMinor) {}
}
