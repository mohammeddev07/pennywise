package com.axel.pennywise.domain.transaction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookRepository;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.category.CategoryType;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reproduces the export endpoint's real request lifecycle: {@code listForExport} runs (and closes)
 * its own read-only transaction, same as it does in production behind TransactionController#export,
 * and {@code writeXlsx} then runs with no active Hibernate session - same as it does inside the
 * StreamingResponseBody callback, which executes after the controller method has returned
 * (spring.jpa.open-in-view=false). Unlike the Mockito-based TransactionExportServiceTest, the
 * TransactionEntity rows here come from a real repository query against a real database, so
 * TransactionEntity.book is a genuine uninitialized Hibernate proxy, not a hand-built object - this
 * is the only test in the suite that can catch a lazy-association access reintroduced into the
 * write path.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TransactionExportIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("pennywise_test")
          .withUsername("postgres")
          .withPassword("postgres");

  @Autowired private UserRepository userRepo;
  @Autowired private BookRepository bookRepo;
  @Autowired private CategoryRepository categoryRepo;
  @Autowired private TransactionRepository txRepo;
  @Autowired private TransactionExportService exportService;

  private UUID bookId;
  private String currencyCode;

  @BeforeEach
  void seed() {
    UserEntity user = new UserEntity();
    user.setAuthSubject("export-it-" + UUID.randomUUID());
    user = userRepo.save(user);

    BookEntity book = new BookEntity();
    book.setOwner(user);
    book.setName("Export IT Book");
    book.setCurrencyCode("USD");
    book.setTimezone("UTC");
    book.setOpeningBalanceMinor(0L);
    book = bookRepo.save(book);
    bookId = book.getId();
    currencyCode = book.getCurrencyCode();

    CategoryEntity category = new CategoryEntity();
    category.setBook(book);
    category.setType(CategoryType.EXPENSE);
    category.setName("Groceries");
    category = categoryRepo.save(category);

    TransactionEntity tx = new TransactionEntity();
    tx.setBook(book);
    tx.setCategory(category);
    tx.setType(TransactionType.EXPENSE);
    tx.setAmountMinor(1500L);
    tx.setOccurredOn(LocalDate.of(2026, 2, 1));
    tx.setTitle("Groceries run");
    txRepo.save(tx);

    // Nothing above is wrapped in @Transactional on this test method - each save() commits and
    // closes its own session, exactly like the real request path. By the time this method
    // returns, no Hibernate session is open anywhere.
  }

  @Test
  void exportSucceedsWithoutAnOpenSessionForTheReadRows() throws Exception {
    // Same call the controller makes: its own short-lived read-only transaction, closed by the
    // time this line returns.
    List<TransactionEntity> rows = txRepo.listForExport(bookId, null, null, null, null);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    // No session is open here - this is the real condition writeXlsx runs under inside the
    // StreamingResponseBody callback. The old code's tx.getBook().getCurrencyCode() would throw
    // org.hibernate.LazyInitializationException at this line.
    assertDoesNotThrow(() -> exportService.writeXlsx(rows, currencyCode, out));

    byte[] xlsx = out.toByteArray();
    assertTrue(xlsx.length > 0);

    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      Row dataRow = wb.getSheet("Transactions").getRow(1);
      assertEquals("USD", dataRow.getCell(8).getStringCellValue());
    }
  }
}
