package com.axel.pennywise.domain.transaction;

import com.axel.pennywise.exception.ApiException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.http.HttpStatus;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * Reads the "Transactions" sheet of an .xlsx file row-by-row via POI's SAX/event API, so memory
 * stays flat regardless of row count (no WorkbookFactory.create(), no full-workbook DOM).
 */
final class TransactionRowParser {

  private static final String SHEET_NAME = "Transactions";
  private static final int HEADER_ROW = 0;

  private TransactionRowParser() {}

  @FunctionalInterface
  interface RowConsumer {
    void accept(RawTransactionRow row);
  }

  static void parse(InputStream xlsx, int maxDataRows, RowConsumer consumer) {
    try (OPCPackage pkg = OPCPackage.open(xlsx)) {
      XSSFReader reader = new XSSFReader(pkg);
      StylesTable styles = reader.getStylesTable();
      SharedStrings strings = reader.getSharedStringsTable();

      InputStream sheetStream = findSheet(reader);

      Handler handler = new Handler(maxDataRows, consumer);
      // Numeric cells (Date/Time/Amount) are read as their raw value, not the cell's display
      // format - avoids misreading e.g. an accounting-style "(7.26)" negative or a locale-specific
      // date format as text. String cells (shared/inline strings) are unaffected by this.
      DataFormatter rawNumberFormatter =
          new DataFormatter() {
            @Override
            public String formatRawCellContents(double value, int formatIndex, String formatString) {
              return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            }
          };
      XSSFSheetXMLHandler sheetHandler =
          new XSSFSheetXMLHandler(styles, null, strings, handler, rawNumberFormatter, false);

      XMLReader xmlReader = XMLHelper.newXMLReader();
      xmlReader.setContentHandler(sheetHandler);
      xmlReader.parse(new InputSource(sheetStream));

      if (!handler.headerSeen) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST, "EMPTY_FILE", "'" + SHEET_NAME + "' sheet is empty");
      }
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CORRUPT_FILE", "Could not read .xlsx file");
    }
  }

  private static InputStream findSheet(XSSFReader reader) throws Exception {
    XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
    while (sheets.hasNext()) {
      InputStream next = sheets.next();
      if (SHEET_NAME.equalsIgnoreCase(sheets.getSheetName())) {
        return next;
      }
    }
    throw new ApiException(
        HttpStatus.BAD_REQUEST, "SHEET_NOT_FOUND", "Workbook has no '" + SHEET_NAME + "' sheet");
  }

  private static final class Handler implements SheetContentsHandler {
    private static final String[] REQUIRED_HEADERS = {"date", "description", "amount", "type"};

    private final int maxDataRows;
    private final RowConsumer consumer;
    private final Map<String, String> headerToColumn = new LinkedHashMap<>();
    private Map<String, String> currentRowValues;
    private boolean headerSeen = false;
    private int dataRowCount = 0;

    Handler(int maxDataRows, RowConsumer consumer) {
      this.maxDataRows = maxDataRows;
      this.consumer = consumer;
    }

    @Override
    public void startRow(int rowNum) {
      currentRowValues = new LinkedHashMap<>();
    }

    @Override
    public void endRow(int rowNum) {
      if (rowNum == HEADER_ROW) {
        currentRowValues.forEach(
            (col, value) -> {
              if (value != null && !value.isBlank()) {
                headerToColumn.put(value.trim().toLowerCase(Locale.ROOT), col);
              }
            });
        headerSeen = true;
        for (String required : REQUIRED_HEADERS) {
          if (!headerToColumn.containsKey(required)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_TEMPLATE",
                "Missing required column header: " + required);
          }
        }
        return;
      }

      if (currentRowValues.isEmpty()) {
        // Row exists in the sheet XML (e.g. pre-formatted template padding) but has no cells at
        // all - nothing to report, and it must not count against the row cap.
        return;
      }

      dataRowCount++;
      if (dataRowCount > maxDataRows) {
        throw new ApiException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "ROW_LIMIT_EXCEEDED",
            "File has more than " + maxDataRows + " data rows");
      }

      consumer.accept(toRawRow(rowNum + 1, currentRowValues));
    }

    @Override
    public void cell(String cellReference, String formattedValue, XSSFComment comment) {
      String col = cellReference.replaceAll("\\d", "");
      currentRowValues.put(col, formattedValue);
    }

    private RawTransactionRow toRawRow(int spreadsheetRowNumber, Map<String, String> byCol) {
      return new RawTransactionRow(
          spreadsheetRowNumber,
          valueFor("date", byCol),
          valueFor("time", byCol),
          valueFor("description", byCol),
          valueFor("amount", byCol),
          valueFor("type", byCol),
          valueFor("category", byCol),
          valueFor("paymentmethod", byCol),
          valueFor("notes", byCol),
          valueFor("currency", byCol),
          valueFor("externalid", byCol));
    }

    private String valueFor(String headerKey, Map<String, String> byCol) {
      String col = headerToColumn.get(headerKey);
      return col == null ? null : byCol.get(col);
    }
  }
}
