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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock private BudgetRepository budgetRepo;
    @Mock private CategoryRepository categoryRepo;
    @Mock private TransactionRepository txRepo;

    private BudgetService service;
    private BookEntity book;
    private CategoryEntity category;
    private UUID bookId;
    private UUID categoryId;
    private UUID budgetId;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgetRepo, categoryRepo, txRepo);

        bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        categoryId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        budgetId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        book = new BookEntity();
        book.setId(bookId);
        book.setCurrencyCode("USD");

        category = new CategoryEntity();
        category.setId(categoryId);
        category.setBook(book);
        category.setName("Food");
        category.setType(CategoryType.EXPENSE);
    }

    @Test
    void upsert_createsBudgetAndIncludesSpentAmount() {
        YearMonth month = YearMonth.of(2026, 1);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 2, 1);

        when(categoryRepo.findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId)).thenReturn(Optional.of(category));
        when(budgetRepo.findActive(bookId, categoryId, start)).thenReturn(Optional.empty());
        when(budgetRepo.save(any(BudgetEntity.class))).thenAnswer(inv -> savedBudget(inv.getArgument(0), 0L));
        when(txRepo.sumByCategory(bookId, TransactionType.EXPENSE, start, end))
                .thenReturn(List.of(new CategoryTotal(categoryId, "Food", CategoryType.EXPENSE, 1_200L)));

        BudgetResponse response = service.upsert(book, categoryId, month, 5_000L, null);

        assertEquals(budgetId, response.id());
        assertEquals(5_000L, response.amountMinor());
        assertEquals(1_200L, response.spentMinor());
        assertEquals(3_800L, response.remainingMinor());
        assertEquals("USD", response.currencyCode());

        verify(budgetRepo).save(argThat(b -> b.getBook() == book
                && b.getCategory() == category
                && start.equals(b.getMonthStart())
                && b.getAmountMinor() == 5_000L));
    }

    @Test
    void upsert_rejectsIncomeCategory() {
        category.setType(CategoryType.INCOME);
        when(categoryRepo.findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId)).thenReturn(Optional.of(category));

        ApiException ex = assertThrows(ApiException.class, () ->
                service.upsert(book, categoryId, YearMonth.of(2026, 1), 5_000L, null)
        );

        assertEquals("Budgets can only be assigned to expense categories", ex.getMessage());
        verifyNoInteractions(txRepo);
    }

    @Test
    void delete_checksIfMatchWhenProvided() {
        YearMonth month = YearMonth.of(2026, 1);
        BudgetEntity budget = existingBudget(month.atDay(1), 2L);
        when(budgetRepo.findActive(bookId, categoryId, month.atDay(1))).thenReturn(Optional.of(budget));

        ApiException ex = assertThrows(ApiException.class, () ->
                service.delete(book, categoryId, month, "\"1\"")
        );

        assertEquals("Resource was modified. Re-fetch and retry.", ex.getMessage());
        verify(budgetRepo, never()).save(any());
    }

    private BudgetEntity savedBudget(BudgetEntity budget, long version) {
        budget.setId(budgetId);
        budget.setCreatedAt(OffsetDateTime.now());
        budget.setUpdatedAt(OffsetDateTime.now());
        budget.setVersion(version);
        return budget;
    }

    private BudgetEntity existingBudget(LocalDate monthStart, long version) {
        BudgetEntity budget = new BudgetEntity();
        budget.setId(budgetId);
        budget.setBook(book);
        budget.setCategory(category);
        budget.setMonthStart(monthStart);
        budget.setAmountMinor(5_000L);
        budget.setVersion(version);
        budget.setCreatedAt(OffsetDateTime.now());
        budget.setUpdatedAt(OffsetDateTime.now());
        return budget;
    }
}
