package com.axel.pennywise.domain.transaction;

import static org.junit.jupiter.api.Assertions.*;

import com.axel.pennywise.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TransactionRowParserTest {

  @Test
  void parsesRealTemplateSampleData() throws IOException {
    List<RawTransactionRow> rows = new ArrayList<>();
    try (InputStream in =
        getClass().getResourceAsStream("/TransactionsTemplate.xlsx")) {
      assertNotNull(in, "TransactionsTemplate.xlsx must be on the test classpath");
      TransactionRowParser.parse(in, 5000, rows::add);
    }
    assertFalse(rows.isEmpty());

    RawTransactionRow first = rows.get(0);
    assertEquals(2, first.rowNumber());
    assertEquals("ChatGPT", first.description());
    assertEquals("-7.26", first.amount());
    assertEquals("Expense", first.type());
    assertEquals("Uncategorized", first.category());
    assertEquals("Online", first.paymentMethod());
    assertEquals("USD", first.currency());
  }

  @Test
  void readsColumnsByHeaderNameRegardlessOfOrder() {
    byte[] xlsx =
        workbook(
            new String[] {"Amount", "Date", "Description", "Type", "Category"},
            new String[][] {{"-12.34", "2026-01-05", "Coffee", "Expense", "Food"}});

    List<RawTransactionRow> rows = new ArrayList<>();
    TransactionRowParser.parse(new ByteArrayInputStream(xlsx), 1000, rows::add);

    assertEquals(1, rows.size());
    RawTransactionRow row = rows.get(0);
    assertEquals(2, row.rowNumber());
    assertEquals("-12.34", row.amount());
    assertEquals("2026-01-05", row.date());
    assertEquals("Coffee", row.description());
    assertEquals("Expense", row.type());
    assertEquals("Food", row.category());
  }

  @Test
  void blankRowIsReportedAsBlank() {
    byte[] xlsx =
        workbook(
            new String[] {"Date", "Description", "Amount", "Type"},
            new String[][] {{"", "", "", ""}});

    List<RawTransactionRow> rows = new ArrayList<>();
    TransactionRowParser.parse(new ByteArrayInputStream(xlsx), 1000, rows::add);

    assertEquals(1, rows.size());
    assertTrue(rows.get(0).isBlank());
  }

  @Test
  void missingRequiredHeaderThrows() {
    byte[] xlsx =
        workbook(new String[] {"Date", "Description", "Type"}, new String[][] {{"a", "b", "c"}});

    ApiException ex =
        assertThrows(
            ApiException.class,
            () -> TransactionRowParser.parse(new ByteArrayInputStream(xlsx), 1000, r -> {}));
    assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    assertEquals("INVALID_TEMPLATE", ex.code());
  }

  @Test
  void wrongSheetNameThrows() {
    byte[] xlsx =
        workbookWithSheetName(
            "Sheet1", new String[] {"Date", "Description", "Amount", "Type"}, new String[][] {});

    ApiException ex =
        assertThrows(
            ApiException.class,
            () -> TransactionRowParser.parse(new ByteArrayInputStream(xlsx), 1000, r -> {}));
    assertEquals("SHEET_NOT_FOUND", ex.code());
  }

  @Test
  void exceedingRowCapThrows() {
    String[][] data = new String[5][];
    for (int i = 0; i < data.length; i++) {
      data[i] = new String[] {"2026-01-0" + (i + 1), "d", "1.00", "Income"};
    }
    byte[] xlsx = workbook(new String[] {"Date", "Description", "Amount", "Type"}, data);

    ApiException ex =
        assertThrows(
            ApiException.class,
            () -> TransactionRowParser.parse(new ByteArrayInputStream(xlsx), 3, r -> {}));
    assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.status());
    assertEquals("ROW_LIMIT_EXCEEDED", ex.code());
  }

  static byte[] workbook(String[] headers, String[][] rows) {
    return workbookWithSheetName("Transactions", headers, rows);
  }

  static byte[] workbookWithSheetName(String sheetName, String[] headers, String[][] rows) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet(sheetName);
      Row header = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
      }
      for (int r = 0; r < rows.length; r++) {
        Row row = sheet.createRow(r + 1);
        for (int c = 0; c < rows[r].length; c++) {
          row.createCell(c).setCellValue(rows[r][c]);
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      wb.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
