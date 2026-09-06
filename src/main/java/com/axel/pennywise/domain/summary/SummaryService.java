package com.axel.pennywise.domain.summary;

import com.axel.pennywise.api.dto.summary.BalanceResponse;
import com.axel.pennywise.api.dto.summary.CategoryBreakdownItem;
import com.axel.pennywise.api.dto.summary.DailyBreakdownItem;
import com.axel.pennywise.api.dto.summary.MonthlySummaryResponse;
import com.axel.pennywise.api.dto.summary.RangeSummaryResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.budget.BudgetRepository;
import com.axel.pennywise.domain.transaction.TransactionRepository;
import com.axel.pennywise.domain.transaction.TransactionType;
import com.axel.pennywise.exception.ApiException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

  private final TransactionRepository txRepo;
  private final BudgetRepository budgetRepo;

  @Transactional(readOnly = true)
  public BalanceResponse balance(BookEntity book) {
    SummaryTotalsView totals = txRepo.sumTotalsAll(book.getId());

    long opening = book.getOpeningBalanceMinor();
    long balance = opening + totals.getIncomeTotalMinor() - totals.getExpenseTotalMinor();

    return new BalanceResponse(book.getId(), book.getCurrencyCode(), balance);
  }

  @Transactional(readOnly = true)
  public MonthlySummaryResponse monthly(BookEntity book, YearMonth ym) {
    LocalDate start = ym.atDay(1);
    LocalDate endExclusive = ym.plusMonths(1).atDay(1);

    // Use the month window
    SummaryTotalsView totals = txRepo.sumTotalsRange(book.getId(), start, endExclusive);

    Map<UUID, Long> budgetsByCategory =
        budgetRepo.findAllActiveForBookAndMonth(book.getId(), start).stream()
            .collect(
                Collectors.toMap(b -> b.getCategory().getId(), b -> b.getAmountMinor(), Long::sum));

    List<CategoryBreakdownItem> byCategory =
        txRepo.sumByCategory(book.getId(), TransactionType.EXPENSE, start, endExclusive).stream()
            .map(
                ct ->
                    new CategoryBreakdownItem(
                        ct.categoryId(),
                        ct.categoryName(),
                        ct.type(),
                        ct.totalMinor(),
                        budgetsByCategory.get(ct.categoryId()),
                        ct.transactionCount()))
            .toList();

    return new MonthlySummaryResponse(
        book.getId(),
        ym.toString(), // "YYYY-MM"
        book.getCurrencyCode(),
        totals.getIncomeTotalMinor(),
        totals.getExpenseTotalMinor(),
        byCategory);
  }

  private static final int MAX_RANGE_YEARS = 5;

  @Transactional(readOnly = true)
  public RangeSummaryResponse range(BookEntity book, LocalDate startDate, LocalDate endDate) {
    LocalDate endExclusive = endDate.plusDays(1);

    SummaryTotalsView totals = txRepo.sumTotals(book.getId(), startDate, endExclusive);
    long transactionCount = txRepo.countForRange(book.getId(), startDate, endExclusive);

    List<CategoryBreakdownItem> byCategory = new ArrayList<>();
    for (TransactionType type : TransactionType.values()) {
      txRepo.sumByCategory(book.getId(), type, startDate, endExclusive).stream()
          .map(
              ct ->
                  new CategoryBreakdownItem(
                      ct.categoryId(),
                      ct.categoryName(),
                      ct.type(),
                      ct.totalMinor(),
                      null,
                      ct.transactionCount()))
          .forEach(byCategory::add);
    }

    Map<LocalDate, DailyTotal> byDate =
        txRepo.sumByDay(book.getId(), startDate, endExclusive).stream()
            .collect(Collectors.toMap(DailyTotal::occurredOn, d -> d));

    List<DailyBreakdownItem> byDay = new ArrayList<>();
    for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
      DailyTotal dt = byDate.get(d);
      byDay.add(
          new DailyBreakdownItem(
              d, dt == null ? 0 : dt.incomeTotalMinor(), dt == null ? 0 : dt.expenseTotalMinor()));
    }

    return new RangeSummaryResponse(
        book.getId(),
        startDate,
        endDate,
        book.getCurrencyCode(),
        totals.getIncomeTotalMinor(),
        totals.getExpenseTotalMinor(),
        transactionCount,
        byCategory,
        byDay);
  }

  public void validateRangeOrThrow(LocalDate startDate, LocalDate endDate) {
    if (startDate.isAfter(endDate)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "startDate must be on or before endDate");
    }
    if (endDate.isAfter(startDate.plusYears(MAX_RANGE_YEARS))) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "Range cannot exceed " + MAX_RANGE_YEARS + " years");
    }
  }

  public YearMonth parseMonthOrThrow(String month) {
    if (month == null || month.isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "month is required (YYYY-MM)");
    }
    try {
      return YearMonth.parse(month.trim());
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid month format. Use YYYY-MM");
    }
  }
}
