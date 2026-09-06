package com.axel.pennywise.domain.transaction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.axel.pennywise.api.dto.transaction.ImportResult;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryService;
import com.axel.pennywise.domain.category.CategoryType;
import com.axel.pennywise.exception.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

  private static final String[] COLUMNS = {
    "Date",
    "Time",
    "Description",
    "Amount",
    "Type",
    "Category",
    "PaymentMethod",
    "Notes",
    "Currency",
    "ExternalId"
  };

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

  private MockMultipartFile file(String[][] rows) {
    return new MockMultipartFile(
        "file", "transactions.xlsx", "application/octet-stream", workbook(rows));
  }

  private byte[] workbook(String[][] rows) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Transactions");
      Row header = sheet.createRow(0);
      for (int i = 0; i < COLUMNS.length; i++) {
        header.createCell(i).setCellValue(COLUMNS[i]);
      }
      for (int r = 0; r < rows.length; r++) {
        Row row = sheet.createRow(r + 1);
        for (int c = 0; c < rows[r].length; c++) {
          if (rows[r][c] != null) {
            row.createCell(c).setCellValue(rows[r][c]);
          }
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      wb.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private CategoryEntity someCategory(CategoryType type, String name) {
    CategoryEntity c = new CategoryEntity();
    c.setId(UUID.randomUUID());
    c.setBook(book);
    c.setType(type);
    c.setName(name);
    return c;
  }

  @Test
  void importsValidExpenseAndIncomeRows() {
    when(categoryService.getOrCreateForImport(eq(book), eq(CategoryType.EXPENSE), eq("Food")))
        .thenReturn(
            new CategoryService.CategoryLookupResult(
                someCategory(CategoryType.EXPENSE, "Food"), false));
    when(categoryService.getOrCreateForImport(eq(book), eq(CategoryType.INCOME), eq("Salary")))
        .thenReturn(
            new CategoryService.CategoryLookupResult(
                someCategory(CategoryType.INCOME, "Salary"), false));

    MockMultipartFile f =
        file(
            new String[][] {
              {"2026-01-05", "14:30", "Coffee", "-12.34", "Expense", "Food"},
              {"2026-01-06", null, "Paycheck", "2500.00", "Income", "Salary"},
            });

    ImportResult result = importService.importXlsx(book, f);

    assertEquals(2, result.totalRows());
    assertEquals(2, result.importedCount());
    assertEquals(0, result.failedCount());
    assertEquals(0, result.skippedBlankCount());
    assertEquals(0, result.skippedDuplicateCount());

    verify(txService)
        .create(
            eq(book),
            any(),
            eq(TransactionType.EXPENSE),
            eq(1234L),
            eq(LocalDate.of(2026, 1, 5)),
            isNull(),
            eq("Coffee"),
            isNull(),
            notNull(),
            isNull());
    verify(txService)
        .create(
            eq(book),
            any(),
            eq(TransactionType.INCOME),
            eq(250000L),
            eq(LocalDate.of(2026, 1, 6)),
            isNull(),
            eq("Paycheck"),
            isNull(),
            isNull(),
            isNull());
  }

  @Test
  void blankRowIsSkippedNotErrored() {
    MockMultipartFile f = file(new String[][] {{"", "", "", "", "", ""}});

    ImportResult result = importService.importXlsx(book, f);

    assertEquals(1, result.totalRows());
    assertEquals(1, result.skippedBlankCount());
    assertEquals(0, result.failedCount());
    verifyNoInteractions(txService);
  }

  @Test
  void missingRequiredFieldIsRejectedWithRowNumber() {
    MockMultipartFile f =
        file(new String[][] {{"2026-01-05", null, null, "-12.34", "Expense", "Food"}});

    ImportResult result = importService.importXlsx(book, f);

    assertEquals(1, result.failedCount());
    assertEquals(2, result.errors().get(0).rowNumber());
    assertEquals("MISSING_REQUIRED_FIELD", result.errors().get(0).code());
    verifyNoInteractions(txService);
  }

  @Test
  void amountSignMustMatchType() {
    MockMultipartFile f =
        file(new String[][] {{"2026-01-05", null, "Coffee", "12.34", "Expense", "Food"}});

    ImportResult result = importService.importXlsx(book, f);

    assertEquals(1, result.failedCount());
    assertEquals("AMOUNT_TYPE_MISMATCH", result.errors().get(0).code());
    verifyNoInteractions(txService);
  }

  @Test
  void blankCategoryDefaultsToUncategorized() {
    when(categoryService.getOrCreateForImport(
            eq(book), eq(CategoryType.EXPENSE), eq("Uncategorized")))
        .thenReturn(
            new CategoryService.CategoryLookupResult(
                someCategory(CategoryType.EXPENSE, "Uncategorized"), true));

    MockMultipartFile f =
        file(new String[][] {{"2026-01-05", null, "Coffee", "-12.34", "Expense", null}});

    ImportResult result = importService.importXlsx(book, f);

    assertEquals(1, result.importedCount());
    assertEquals(1, result.categoriesCreated().size());
    assertEquals("Uncategorized", result.categoriesCreated().get(0));
  }

  @Test
  void unknownPaymentMethodFallsBackToOther() {
    when(categoryService.getOrCreateForImport(any(), any(), any()))
        .thenReturn(
            new CategoryService.CategoryLookupResult(
                someCategory(CategoryType.EXPENSE, "Food"), false));

    MockMultipartFile f =
        file(new String[][] {{"2026-01-05", null, "Coffee", "-12.34", "Expense", "Food", "Zelle"}});

    importService.importXlsx(book, f);

    verify(txService)
        .create(
            any(),
            any(),
            any(),
            anyLong(),
            any(),
            any(),
            any(),
            eq(PaymentMethod.OTHER),
            any(),
            any());
  }

  @Test
  void currencyMismatchIsRejected() {
    MockMultipartFile f =
        file(
            new String[][] {
              {"2026-01-05", null, "Coffee", "-12.34", "Expense", "Food", null, null, "EUR"}
            });

    ImportResult result = importService.importXlsx(book, f);

    assertEquals(1, result.failedCount());
    assertEquals("CURRENCY_MISMATCH", result.errors().get(0).code());
    verifyNoInteractions(txService);
  }

  @Test
  void duplicateExternalIdIsSkippedNotErrored() {
    when(categoryService.getOrCreateForImport(any(), any(), any()))
        .thenReturn(
            new CategoryService.CategoryLookupResult(
                someCategory(CategoryType.EXPENSE, "Food"), false));
    when(txRepo.existsByBook_IdAndExternalIdAndDeletedAtIsNull(book.getId(), "ext-1"))
        .thenReturn(true);

    MockMultipartFile f =
        file(
            new String[][] {
              {"2026-01-05", null, "Coffee", "-12.34", "Expense", "Food", null, null, null, "ext-1"}
            });

    ImportResult result = importService.importXlsx(book, f);

    assertEquals(1, result.skippedDuplicateCount());
    assertEquals(0, result.importedCount());
    assertEquals(0, result.failedCount());
    verify(txService, never())
        .create(any(), any(), any(), anyLong(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void duplicateBySelfIdIsSkippedNotErrored() {
    UUID existingId = UUID.randomUUID();
    when(categoryService.getOrCreateForImport(any(), any(), any()))
        .thenReturn(
            new CategoryService.CategoryLookupResult(
                someCategory(CategoryType.EXPENSE, "Food"), false));
    when(txRepo.existsByBook_IdAndIdAndDeletedAtIsNull(book.getId(), existingId)).thenReturn(true);

    MockMultipartFile f =
        file(
            new String[][] {
              {
                "2026-01-05",
                null,
                "Coffee",
                "-12.34",
                "Expense",
                "Food",
                null,
                null,
                null,
                existingId.toString()
              }
            });

    ImportResult result = importService.importXlsx(book, f);

    assertEquals(1, result.skippedDuplicateCount());
    assertEquals(0, result.importedCount());
    assertEquals(0, result.failedCount());
    verify(txService, never())
        .create(any(), any(), any(), anyLong(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void uuidLookingExternalIdWithNoMatchImportsAsNew() {
    when(categoryService.getOrCreateForImport(any(), any(), any()))
        .thenReturn(
            new CategoryService.CategoryLookupResult(
                someCategory(CategoryType.EXPENSE, "Food"), false));

    String uuidLookingButUnknown = UUID.randomUUID().toString();
    MockMultipartFile f =
        file(
            new String[][] {
              {
                "2026-01-05",
                null,
                "Coffee",
                "-12.34",
                "Expense",
                "Food",
                null,
                null,
                null,
                uuidLookingButUnknown
              }
            });

    ImportResult result = importService.importXlsx(book, f);

    assertEquals(1, result.importedCount());
    assertEquals(0, result.skippedDuplicateCount());
    verify(txService)
        .create(
            eq(book),
            any(),
            eq(TransactionType.EXPENSE),
            eq(1234L),
            any(),
            isNull(),
            eq("Coffee"),
            isNull(),
            any(),
            eq(uuidLookingButUnknown));
  }

  @Test
  void rejectsNonXlsxFile() {
    MockMultipartFile f =
        new MockMultipartFile("file", "transactions.csv", "text/csv", "a,b,c".getBytes());

    ApiException ex = assertThrows(ApiException.class, () -> importService.importXlsx(book, f));
    assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    assertEquals("INVALID_FILE_TYPE", ex.code());
  }

  /**
   * Reproduces a real-world Excel export: negative amounts styled as accounting format ("(12.34)"
   * instead of "-12.34") and a free-text payment method not in the enum, mixed with one genuinely
   * invalid ($0 amount) row that must be rejected without affecting the others.
   */
  @Test
  void importsAccountingFormattedRowsAndRejectsZeroAmountRow() throws IOException {
    when(categoryService.getOrCreateForImport(any(), any(), any()))
        .thenAnswer(
            inv ->
                new CategoryService.CategoryLookupResult(
                    someCategory(inv.getArgument(1), inv.getArgument(2)), false));

    byte[] xlsx = accountingStyleWorkbook();
    MockMultipartFile f =
        new MockMultipartFile("file", "transactions.xlsx", "application/octet-stream", xlsx);

    ImportResult result = importService.importXlsx(book, f);

    assertEquals(3, result.totalRows());
    assertEquals(1, result.failedCount(), () -> result.errors().toString());
    assertEquals(4, result.errors().get(0).rowNumber());
    assertEquals("INVALID_AMOUNT", result.errors().get(0).code());
    assertEquals(2, result.importedCount());

    verify(txService)
        .create(
            eq(book),
            any(),
            eq(TransactionType.EXPENSE),
            eq(1234L),
            any(),
            any(),
            any(),
            eq(PaymentMethod.OTHER),
            any(),
            isNull());
    verify(txService)
        .create(
            eq(book),
            any(),
            eq(TransactionType.INCOME),
            eq(250000L),
            any(),
            any(),
            any(),
            isNull(),
            any(),
            isNull());
  }

  private static byte[] accountingStyleWorkbook() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Transactions");

      CellStyle accountingStyle = wb.createCellStyle();
      accountingStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00;(#,##0.00)"));

      Row header = sheet.createRow(0);
      for (int i = 0; i < COLUMNS.length; i++) {
        header.createCell(i).setCellValue(COLUMNS[i]);
      }

      writeRow(
          sheet,
          accountingStyle,
          1,
          LocalDate.of(2026, 1, 5),
          "Coffee",
          -12.34,
          "Expense",
          "Food",
          "Zelle");
      writeRow(
          sheet,
          accountingStyle,
          2,
          LocalDate.of(2026, 1, 6),
          "Paycheck",
          2500.00,
          "Income",
          "Salary",
          null);
      writeRow(
          sheet,
          accountingStyle,
          3,
          LocalDate.of(2026, 1, 7),
          "Zero",
          0.00,
          "Expense",
          "Food",
          null);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      wb.write(out);
      return out.toByteArray();
    }
  }

  private static void writeRow(
      Sheet sheet,
      CellStyle accountingStyle,
      int rowIndex,
      LocalDate date,
      String description,
      double amount,
      String type,
      String category,
      String paymentMethod) {
    Row row = sheet.createRow(rowIndex);
    row.createCell(0).setCellValue(date);
    row.createCell(2).setCellValue(description);
    Cell amountCell = row.createCell(3);
    amountCell.setCellValue(amount);
    amountCell.setCellStyle(accountingStyle);
    row.createCell(4).setCellValue(type);
    row.createCell(5).setCellValue(category);
    if (paymentMethod != null) {
      row.createCell(6).setCellValue(paymentMethod);
    }
  }

  @Test
  void rejectsFileOverSizeCap() {
    byte[] oversized = new byte[(int) TransactionImportService.MAX_FILE_BYTES + 1];
    MockMultipartFile f =
        new MockMultipartFile("file", "transactions.xlsx", "application/octet-stream", oversized);

    ApiException ex = assertThrows(ApiException.class, () -> importService.importXlsx(book, f));
    assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.status());
    assertEquals("FILE_TOO_LARGE", ex.code());
  }
}
