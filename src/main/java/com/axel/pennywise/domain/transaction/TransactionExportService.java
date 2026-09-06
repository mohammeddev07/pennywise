package com.axel.pennywise.domain.transaction;

import com.axel.pennywise.exception.ApiException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Writes transactions to the standard "Transactions" .xlsx layout via POI's streaming writer. */
@Service
public class TransactionExportService {

  private static final String[] HEADERS = {
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

  public void writeXlsx(List<TransactionEntity> transactions, OutputStream out) {
    try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
      Sheet sheet = workbook.createSheet("Transactions");

      CellStyle dateStyle = workbook.createCellStyle();
      dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
      CellStyle timeStyle = workbook.createCellStyle();
      timeStyle.setDataFormat(workbook.createDataFormat().getFormat("hh:mm"));
      CellStyle amountStyle = workbook.createCellStyle();
      amountStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));

      Row header = sheet.createRow(0);
      for (int i = 0; i < HEADERS.length; i++) {
        header.createCell(i).setCellValue(HEADERS[i]);
      }

      int rowNum = 1;
      for (TransactionEntity tx : transactions) {
        writeRow(sheet.createRow(rowNum++), tx, dateStyle, timeStyle, amountStyle);
      }

      workbook.write(out);
    } catch (IOException e) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Failed to write export file");
    }
  }

  private void writeRow(
      Row row,
      TransactionEntity tx,
      CellStyle dateStyle,
      CellStyle timeStyle,
      CellStyle amountStyle) {
    Cell dateCell = row.createCell(0);
    dateCell.setCellValue(tx.getOccurredOn());
    dateCell.setCellStyle(dateStyle);

    if (tx.getOccurredAt() != null) {
      Cell timeCell = row.createCell(1);
      timeCell.setCellValue(tx.getOccurredAt().toLocalTime().toSecondOfDay() / 86400.0);
      timeCell.setCellStyle(timeStyle);
    }

    row.createCell(2).setCellValue(tx.getTitle() == null ? "" : tx.getTitle());

    BigDecimal minor = BigDecimal.valueOf(tx.getAmountMinor(), 2);
    BigDecimal signedAmount = tx.getType() == TransactionType.EXPENSE ? minor.negate() : minor;
    Cell amountCell = row.createCell(3);
    amountCell.setCellValue(signedAmount.setScale(2, RoundingMode.UNNECESSARY).doubleValue());
    amountCell.setCellStyle(amountStyle);

    row.createCell(4).setCellValue(tx.getType() == TransactionType.EXPENSE ? "Expense" : "Income");
    row.createCell(5).setCellValue(tx.getCategory().getName());
    row.createCell(6)
        .setCellValue(tx.getPaymentMethod() == null ? "" : tx.getPaymentMethod().name());
    row.createCell(7).setCellValue(tx.getNote() == null ? "" : tx.getNote());
    row.createCell(8).setCellValue(tx.getBook().getCurrencyCode());
    row.createCell(9)
        .setCellValue(tx.getExternalId() != null ? tx.getExternalId() : tx.getId().toString());
  }
}
