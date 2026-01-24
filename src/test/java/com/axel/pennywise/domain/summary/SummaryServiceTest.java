package com.axel.pennywise.domain.summary;

import com.axel.pennywise.api.dto.summary.BalanceResponse;
import com.axel.pennywise.api.dto.summary.MonthlySummaryResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.transaction.TransactionRepository;
import com.axel.pennywise.domain.transaction.TransactionType;
import com.axel.pennywise.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock private TransactionRepository txRepo;

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
        when(txRepo.sumByCategory(bookId, TransactionType.EXPENSE, start, endExclusive))
                .thenReturn(List.of(food, rent));

        MonthlySummaryResponse resp = summaryService.monthly(book, ym);

        assertEquals(bookId, resp.bookId());
        assertEquals("2026-01", resp.month());
        assertEquals("USD", resp.currencyCode());
        assertEquals(12_000L, resp.incomeTotalMinor());
        assertEquals(7_500L, resp.expenseTotalMinor());

        verify(txRepo).sumTotalsRange(bookId, start, endExclusive);
        verify(txRepo).sumByCategory(bookId, TransactionType.EXPENSE, start, endExclusive);
        verifyNoMoreInteractions(txRepo);
    }

    @Test
    void parseMonthOrThrow_returnsYearMonth_whenValid() {
        YearMonth ym = summaryService.parseMonthOrThrow("2026-01");
        assertEquals(YearMonth.of(2026, 1), ym);
    }

    @Test
    void parseMonthOrThrow_throwsBadRequest_whenBlank() {
        ApiException ex = assertThrows(ApiException.class, () -> summaryService.parseMonthOrThrow("   "));
        assertEquals("month is required (YYYY-MM)", ex.getMessage());

    }

    @Test
    void parseMonthOrThrow_throwsBadRequest_whenInvalidFormat() {
        ApiException ex = assertThrows(ApiException.class, () -> summaryService.parseMonthOrThrow("not-a-month"));
        assertEquals("Invalid month format. Use YYYY-MM", ex.getMessage());
    }
}
