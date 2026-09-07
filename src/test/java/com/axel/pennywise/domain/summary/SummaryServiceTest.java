package com.axel.pennywise.domain.summary;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.axel.pennywise.api.dto.summary.BalanceResponse;
import com.axel.pennywise.api.dto.summary.MonthlySummaryResponse;
import com.axel.pennywise.api.dto.summary.RangeSummaryResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.budget.BudgetRepository;
import com.axel.pennywise.domain.transaction.TransactionRepository;
import com.axel.pennywise.domain.transaction.TransactionType;
import com.axel.pennywise.exception.ApiException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

  @Mock private TransactionRepository txRepo;
  @Mock private BudgetRepository budgetRepo;

  @InjectMocks private SummaryService summaryService;

  private BookEntity book;
  private UUID bookId;

  @BeforeEach
  void setUp() {
    bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    book = new BookEntity();
    book.setId(bookId);
    book.setCurrencyCode("USD");
    book.setOpeningBalanceMinor(10_000L);
  }

  @Test
  void balance_calculatesOpeningPlusIncomeMinusExpense() {
    SummaryTotalsView totals = mock(SummaryTotalsView.class);
    when(totals.getIncomeTotalMinor()).thenReturn(5_000L);
    when(totals.getExpenseTotalMinor()).thenReturn(2_000L);

    when(txRepo.sumTotalsAll(bookId)).thenReturn(totals);

    BalanceResponse resp = summaryService.balance(book);

    // expected = 10,000 + 5,000 - 2,000 = 13,000
    assertEquals(bookId, resp.bookId());
    assertEquals("USD", resp.currencyCode());
    assertEquals(13_000L, resp.balanceMinor());

    verify(txRepo).sumTotalsAll(bookId);
    verifyNoMoreInteractions(txRepo);
  }

  @Test
  void monthly_usesMonthWindow_callsRepo_andMapsCategoryBreakdown() {
    YearMonth ym = YearMonth.of(2026, 1);
    LocalDate start = LocalDate.of(2026, 1, 1);
    LocalDate endExclusive = LocalDate.of(2026, 2, 1);

    SummaryTotalsView totals = mock(SummaryTotalsView.class);
    when(totals.getIncomeTotalMinor()).thenReturn(12_000L);
    when(totals.getExpenseTotalMinor()).thenReturn(7_500L);

    UUID foodId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    UUID rentId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    // CategoryTotalsView is whatever projection your repo returns.
    // We mock it to keep it simple.
    CategoryTotal food = mock(CategoryTotal.class);
    when(food.categoryId()).thenReturn(foodId);
    when(food.totalMinor()).thenReturn(2_000L);

    CategoryTotal rent = mock(CategoryTotal.class);
    when(rent.categoryId()).thenReturn(rentId);
    when(rent.totalMinor()).thenReturn(3_000L);

    when(txRepo.sumTotalsRange(bookId, start, endExclusive)).thenReturn(totals);
    when(budgetRepo.findAllActiveForBookAndMonth(bookId, start)).thenReturn(List.of());
    when(txRepo.sumByCategory(bookId, TransactionType.EXPENSE, start, endExclusive))
        .thenReturn(List.of(food, rent));

    MonthlySummaryResponse resp = summaryService.monthly(book, ym);

    assertEquals(bookId, resp.bookId());
    assertEquals("2026-01", resp.month());
    assertEquals("USD", resp.currencyCode());
    assertEquals(12_000L, resp.incomeTotalMinor());
    assertEquals(7_500L, resp.expenseTotalMinor());

    verify(txRepo).sumTotalsRange(bookId, start, endExclusive);
    verify(budgetRepo).findAllActiveForBookAndMonth(bookId, start);
    verify(txRepo).sumByCategory(bookId, TransactionType.EXPENSE, start, endExclusive);
    verifyNoMoreInteractions(txRepo, budgetRepo);
  }

  @Test
  void range_computesTotalsCountAndZeroFillsMissingDays() {
    LocalDate start = LocalDate.of(2026, 1, 5);
    LocalDate end = LocalDate.of(2026, 1, 8);
    LocalDate endExclusive = LocalDate.of(2026, 1, 9);

    SummaryTotalsView totals = mock(SummaryTotalsView.class);
    when(totals.getIncomeTotalMinor()).thenReturn(5_000L);
    when(totals.getExpenseTotalMinor()).thenReturn(3_000L);
    when(txRepo.sumTotals(bookId, start, endExclusive)).thenReturn(totals);
    when(txRepo.countForRange(bookId, start, endExclusive)).thenReturn(4L);

    UUID foodId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    CategoryTotal food = mock(CategoryTotal.class);
    when(food.categoryId()).thenReturn(foodId);
    when(food.categoryName()).thenReturn("Food");
    when(food.type()).thenReturn(com.axel.pennywise.domain.category.CategoryType.EXPENSE);
    when(food.totalMinor()).thenReturn(3_000L);
    when(food.transactionCount()).thenReturn(2L);

    when(txRepo.sumByCategory(bookId, TransactionType.EXPENSE, start, endExclusive))
        .thenReturn(List.of(food));
    when(txRepo.sumByCategory(bookId, TransactionType.INCOME, start, endExclusive))
        .thenReturn(List.of());

    // Only day 5 and day 8 have transactions - days 6 and 7 must be zero-filled, not skipped.
    when(txRepo.sumByDay(bookId, start, endExclusive))
        .thenReturn(
            List.of(
                new DailyTotal(LocalDate.of(2026, 1, 5), 0L, 1_500L),
                new DailyTotal(LocalDate.of(2026, 1, 8), 5_000L, 1_500L)));

    RangeSummaryResponse resp = summaryService.range(book, start, end);

    assertEquals(bookId, resp.bookId());
    assertEquals(start, resp.startDate());
    assertEquals(end, resp.endDate());
    assertEquals("USD", resp.currencyCode());
    assertEquals(5_000L, resp.incomeTotalMinor());
    assertEquals(3_000L, resp.expenseTotalMinor());
    assertEquals(4L, resp.transactionCount());
    assertEquals(1, resp.byCategory().size());
    assertEquals("Food", resp.byCategory().get(0).categoryName());
    assertEquals(2L, resp.byCategory().get(0).transactionCount());
    assertNull(resp.byCategory().get(0).budgetMinor());

    assertEquals(4, resp.byDay().size());
    assertEquals(LocalDate.of(2026, 1, 5), resp.byDay().get(0).date());
    assertEquals(1_500L, resp.byDay().get(0).expenseTotalMinor());
    assertEquals(LocalDate.of(2026, 1, 6), resp.byDay().get(1).date());
    assertEquals(0L, resp.byDay().get(1).incomeTotalMinor());
    assertEquals(0L, resp.byDay().get(1).expenseTotalMinor());
    assertEquals(LocalDate.of(2026, 1, 7), resp.byDay().get(2).date());
    assertEquals(LocalDate.of(2026, 1, 8), resp.byDay().get(3).date());
    assertEquals(5_000L, resp.byDay().get(3).incomeTotalMinor());
  }

  @Test
  void validateRangeOrThrow_throwsWhenStartAfterEnd() {
    ApiException ex =
        assertThrows(
            ApiException.class,
            () ->
                summaryService.validateRangeOrThrow(
                    LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
    assertEquals("startDate must be on or before endDate", ex.getMessage());
  }

  @Test
  void validateRangeOrThrow_throwsWhenRangeExceedsFiveYears() {
    LocalDate start = LocalDate.of(2020, 1, 1);
    LocalDate end = LocalDate.of(2025, 1, 2); // one day past the 5-year cap
    ApiException ex =
        assertThrows(ApiException.class, () -> summaryService.validateRangeOrThrow(start, end));
    assertEquals("Range cannot exceed 5 years", ex.getMessage());
  }

  @Test
  void validateRangeOrThrow_allowsExactlyFiveYears() {
    LocalDate start = LocalDate.of(2020, 1, 1);
    LocalDate end = LocalDate.of(2025, 1, 1);
    assertDoesNotThrow(() -> summaryService.validateRangeOrThrow(start, end));
  }

  @Test
  void parseMonthOrThrow_returnsYearMonth_whenValid() {
    YearMonth ym = summaryService.parseMonthOrThrow("2026-01");
    assertEquals(YearMonth.of(2026, 1), ym);
  }

  @Test
  void parseMonthOrThrow_throwsBadRequest_whenBlank() {
    ApiException ex =
        assertThrows(ApiException.class, () -> summaryService.parseMonthOrThrow("   "));
    assertEquals("month is required (YYYY-MM)", ex.getMessage());
  }

  @Test
  void parseMonthOrThrow_throwsBadRequest_whenInvalidFormat() {
    ApiException ex =
        assertThrows(ApiException.class, () -> summaryService.parseMonthOrThrow("not-a-month"));
    assertEquals("Invalid month format. Use YYYY-MM", ex.getMessage());
  }
}
