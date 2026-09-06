package com.axel.pennywise.domain.transaction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.axel.pennywise.api.dto.transaction.ImportResult;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryService;
import com.axel.pennywise.domain.category.CategoryType;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Proves the export -> import round trip: a transaction written by TransactionExportService,
 * fed back through TransactionImportService, must produce the same transaction. Exercised via
 * TransactionImportService (not raw string assertions on TransactionRowParser output) because
 * numeric cell formatting details are an implementation detail, not the actual contract.
 */
@ExtendWith(MockitoExtension.class)
class TransactionExportServiceTest {

  private final TransactionExportService exportService = new TransactionExportService();

  @Mock private CategoryService categoryService;
  @Mock private TransactionRepository txRepo;
  @Mock private TransactionService txService;

  private TransactionImportService importService;
  private BookEntity book;

  @BeforeEach
  void setUp() {
    importService = new TransactionImportService(categoryService, txRepo, txService);
    book = new BookEntity();
    book.setId(UUID.randomUUID());
    book.setCurrencyCode("USD");
    book.setTimezone("UTC");
  }

  private MockMultipartFile reimport(TransactionEntity tx) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    exportService.writeXlsx(List.of(tx), out);
    return new MockMultipartFile(
        "file", "export.xlsx", "application/octet-stream", out.toByteArray());
  }

  @Test
  void expenseRoundTripsThroughImport() {
    CategoryEntity category = new CategoryEntity();
    category.setId(UUID.randomUUID());
    category.setBook(book);
    category.setType(CategoryType.EXPENSE);
    category.setName("Food");

    TransactionEntity tx = new TransactionEntity();
    tx.setId(UUID.randomUUID());
    tx.setBook(book);
    tx.setCategory(category);
    tx.setType(TransactionType.EXPENSE);
    tx.setAmountMinor(1234L);
    tx.setOccurredOn(LocalDate.of(2026, 1, 5));
    tx.setOccurredAt(OffsetDateTime.of(2026, 1, 5, 14, 30, 0, 0, ZoneOffset.UTC));
    tx.setTitle("Coffee");
    tx.setPaymentMethod(PaymentMethod.CARD);
    tx.setNote("with a friend");
    tx.setExternalId("ext-1");

    when(categoryService.getOrCreateForImport(book, CategoryType.EXPENSE, "Food"))
        .thenReturn(new CategoryService.CategoryLookupResult(category, false));

    ImportResult result = importService.importXlsx(book, reimport(tx));

    assertEquals(1, result.importedCount());
    assertEquals(0, result.failedCount());

    verify(txService)
        .create(
            eq(book),
            eq(category),
            eq(TransactionType.EXPENSE),
            eq(1234L),
            eq(LocalDate.of(2026, 1, 5)),
            eq("with a friend"),
            eq("Coffee"),
            eq(PaymentMethod.CARD),
            eq(OffsetDateTime.of(2026, 1, 5, 14, 30, 0, 0, ZoneOffset.UTC)),
            eq("ext-1"));
  }

  @Test
  void incomeRoundTripsWithPositiveAmountAndNoTime() {
    CategoryEntity category = new CategoryEntity();
    category.setId(UUID.randomUUID());
    category.setBook(book);
    category.setType(CategoryType.INCOME);
    category.setName("Salary");

    TransactionEntity tx = new TransactionEntity();
    tx.setId(UUID.randomUUID());
    tx.setBook(book);
    tx.setCategory(category);
    tx.setType(TransactionType.INCOME);
    tx.setAmountMinor(250000L);
    tx.setOccurredOn(LocalDate.of(2026, 1, 6));
    tx.setTitle("Paycheck");

    when(categoryService.getOrCreateForImport(book, CategoryType.INCOME, "Salary"))
        .thenReturn(new CategoryService.CategoryLookupResult(category, false));

    ImportResult result = importService.importXlsx(book, reimport(tx));

    assertEquals(1, result.importedCount());
    verify(txService)
        .create(
            eq(book),
            eq(category),
            eq(TransactionType.INCOME),
            eq(250000L),
            eq(LocalDate.of(2026, 1, 6)),
            isNull(),
            eq("Paycheck"),
            isNull(),
            isNull(),
            isNull());
  }
}
