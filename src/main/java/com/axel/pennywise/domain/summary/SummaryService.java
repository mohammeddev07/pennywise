package com.axel.pennywise.domain.summary;

import com.axel.pennywise.api.dto.summary.BalanceResponse;
import com.axel.pennywise.api.dto.summary.CategoryBreakdownItem;
import com.axel.pennywise.api.dto.summary.MonthlySummaryResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.transaction.TransactionRepository;
import com.axel.pennywise.domain.transaction.TransactionType;
import com.axel.pennywise.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final TransactionRepository txRepo;

    @Transactional(readOnly = true)
    public BalanceResponse balance(BookEntity book) {
        SummaryTotalsView totals = txRepo.sumTotals(book.getId(), null, null);

        long opening = book.getOpeningBalanceMinor();
        long balance = opening + totals.getIncomeTotalMinor() - totals.getExpenseTotalMinor();

        return new BalanceResponse(book.getId(), book.getCurrencyCode(), balance);
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse monthly(BookEntity book, YearMonth ym) {
        LocalDate start = ym.atDay(1);
        LocalDate endExclusive = ym.plusMonths(1).atDay(1);

        // Use the month window
        SummaryTotalsView totals = txRepo.sumTotals(book.getId(), start, endExclusive);

        List<CategoryBreakdownItem> byCategory = txRepo
                .sumByCategory(book.getId(), TransactionType.EXPENSE, start, endExclusive)
                .stream()
                .map(ct -> new CategoryBreakdownItem(ct.categoryId(), ct.totalMinor()))
                .toList();

        return new MonthlySummaryResponse(
                book.getId(),
                ym.toString(), // "YYYY-MM"
                book.getCurrencyCode(),
                totals.getIncomeTotalMinor(),
                totals.getExpenseTotalMinor(),
                byCategory
        );
    }

    public YearMonth parseMonthOrThrow(String month) {
        if (month == null || month.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "month is required (YYYY-MM)");
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid month format. Use YYYY-MM");
        }
    }
}
