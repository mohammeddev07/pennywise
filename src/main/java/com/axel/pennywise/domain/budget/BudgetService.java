package com.axel.pennywise.domain.budget;

import com.axel.pennywise.api.dto.budget.BudgetResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.category.CategoryType;
import com.axel.pennywise.domain.summary.CategoryTotal;
import com.axel.pennywise.domain.transaction.TransactionRepository;
import com.axel.pennywise.domain.transaction.TransactionType;
import com.axel.pennywise.exception.ApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepo;
    private final CategoryRepository categoryRepo;
    private final TransactionRepository txRepo;

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(BookEntity book, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        Map<UUID, Long> spentByCategory = spentByCategory(book.getId(), month);

        return budgetRepo.findAllActiveForBookAndMonth(book.getId(), monthStart).stream()
                .map(budget -> toResponse(budget, month, spentByCategory.getOrDefault(budget.getCategory().getId(), 0L)))
                .toList();
    }

    @Transactional
    public BudgetResponse upsert(BookEntity book, UUID categoryId, YearMonth month, long amountMinor, String ifMatch) {
        if (amountMinor < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "amountMinor must be >= 0");
        }

        CategoryEntity category = categoryRepo.findByIdAndBook_IdAndDeletedAtIsNull(categoryId, book.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Category not found"));

        if (category.getType() != CategoryType.EXPENSE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Budgets can only be assigned to expense categories");
        }

        LocalDate monthStart = month.atDay(1);
        BudgetEntity budget = budgetRepo.findActive(book.getId(), categoryId, monthStart)
                .map(existing -> {
                    if (ifMatch != null && !ifMatch.isBlank()) {
                        requireIfMatch(existing.getVersion(), ifMatch);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    BudgetEntity created = new BudgetEntity();
                    created.setBook(book);
                    created.setCategory(category);
                    created.setMonthStart(monthStart);
                    return created;
                });

        budget.setAmountMinor(amountMinor);
        BudgetEntity saved = budgetRepo.save(budget);
        long spentMinor = spentByCategory(book.getId(), month).getOrDefault(categoryId, 0L);
        return toResponse(saved, month, spentMinor);
    }

    @Transactional
    public void delete(BookEntity book, UUID categoryId, YearMonth month, String ifMatch) {
        BudgetEntity budget = budgetRepo.findActive(book.getId(), categoryId, month.atDay(1))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Budget not found"));

        if (ifMatch != null && !ifMatch.isBlank()) {
            requireIfMatch(budget.getVersion(), ifMatch);
        }

        budget.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        budgetRepo.save(budget);
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

    private BudgetResponse toResponse(BudgetEntity budget, YearMonth month, long spentMinor) {
        long remainingMinor = budget.getAmountMinor() - spentMinor;
        return new BudgetResponse(
                budget.getId(),
                budget.getBook().getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                month.toString(),
                budget.getAmountMinor(),
                spentMinor,
                remainingMinor,
                budget.getBook().getCurrencyCode(),
                budget.getCreatedAt(),
                budget.getUpdatedAt(),
                budget.getVersion() == null ? 0 : budget.getVersion()
        );
    }

    private Map<UUID, Long> spentByCategory(UUID bookId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate endExclusive = month.plusMonths(1).atDay(1);
        return txRepo.sumByCategory(bookId, TransactionType.EXPENSE, start, endExclusive).stream()
                .collect(Collectors.toMap(CategoryTotal::categoryId, CategoryTotal::totalMinor, Long::sum));
    }

    private void requireIfMatch(Long currentVersion, String ifMatch) {
        long expected = parseEtagVersion(ifMatch);
        long actual = (currentVersion == null) ? 0L : currentVersion;
        if (expected != actual) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_FAILED,
                    "ETAG_MISMATCH",
                    "Resource was modified. Re-fetch and retry.",
                    List.of(Map.of("expected", String.valueOf(expected), "actual", String.valueOf(actual)))
            );
        }
    }

    private long parseEtagVersion(String ifMatch) {
        String trimmed = ifMatch.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IF_MATCH", "Invalid If-Match value");
        }
    }
}
