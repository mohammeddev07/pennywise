package com.axel.pennywise.domain.transaction;

import static org.junit.jupiter.api.Assertions.*;

import com.axel.pennywise.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TransactionRowParserTest {

  /**
   * Reproduces a real-world Excel quirk: an accounting-style number format displays negative
   * amounts as "(7.26)" instead of "-7.26", and a Date/Time column is stored as a numeric Excel
   * serial, not text. The parser must read the underlying raw value, not the display text -
   * otherwise negative amounts and formatted dates are misread.
   */
  @Test
  void readsRawValueBypassingAccountingStyleDisplayFormat() throws IOException {
    byte[] xlsx = accountingStyleWorkbook();

    List<RawTransactionRow> rows = new ArrayList<>();
    TransactionRowParser.parse(new ByteArrayInputStream(xlsx), 1000, rows::add);

    assertEquals(1, rows.size());
    RawTransactionRow row = rows.get(0);
    assertEquals("ChatGPT", row.description());
    assertEquals("-7.26", row.amount());
    assertEquals("Expense", row.type());
    assertEquals("Uncategorized", row.category());
    assertEquals("Online", row.paymentMethod());
    assertEquals("USD", row.currency());
  }

  private static byte[] accountingStyleWorkbook() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Transactions");

      CellStyle accountingStyle = wb.createCellStyle();
      accountingStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00;(#,##0.00)"));

      String[] headers = {
        "Date", "Description", "Amount", "Type", "Category", "PaymentMethod", "Currency"
      };
      Row header = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
      }

      Row data = sheet.createRow(1);
      Cell dateCell = data.createCell(0);
      dateCell.setCellValue(java.time.LocalDate.of(2023, 11, 20));
      data.createCell(1).setCellValue("ChatGPT");
      Cell amountCell = data.createCell(2);
      amountCell.setCellValue(-7.26);
      amountCell.setCellStyle(accountingStyle);
      data.createCell(3).setCellValue("Expense");
      data.createCell(4).setCellValue("Uncategorized");
      data.createCell(5).setCellValue("Online");
      data.createCell(6).setCellValue("USD");

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      wb.write(out);
      return out.toByteArray();
    }
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

  @Test
  void genuinelyCorruptFileStillProducesCorruptFileError() {
    byte[] notAZipAtAll = "this is not an xlsx file".getBytes();

    ApiException ex =
        assertThrows(
            ApiException.class,
            () ->
                TransactionRowParser.parse(new ByteArrayInputStream(notAZipAtAll), 1000, r -> {}));
    assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    assertEquals("CORRUPT_FILE", ex.code());
  }

  /**
   * A bug in the row consumer (e.g. a NullPointerException) must not be disguised as CORRUPT_FILE -
   * the file itself is perfectly valid here. It must propagate as the real exception so
   * GlobalExceptionHandler logs the actual cause and returns a real 500.
   */
  @Test
  void bugInRowConsumerPropagatesInsteadOfBeingDisguisedAsCorruptFile() {
    byte[] xlsx =
        workbook(
            new String[] {"Date", "Description", "Amount", "Type"},
            new String[][] {{"2026-01-05", "Coffee", "-12.34", "Expense"}});

    assertThrows(
        NullPointerException.class,
        () ->
            TransactionRowParser.parse(
                new ByteArrayInputStream(xlsx),
                1000,
                r -> {
                  throw new NullPointerException("simulated bug in row consumer");
                }));
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
