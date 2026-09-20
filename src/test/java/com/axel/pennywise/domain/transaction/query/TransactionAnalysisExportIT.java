package com.axel.pennywise.domain.transaction.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.axel.pennywise.domain.book.BookRepository;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.category.CategoryType;
import com.axel.pennywise.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * P1.3: analyze + filtered export must agree with search and with independent hand calculations
 * (see {@link QueryTestData} for the arithmetic).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.security.auth-enabled=true")
class TransactionAnalysisExportIT extends AbstractPostgresIT {

  private static final String FULL_START = "2025-01-01";
  private static final String FULL_END = "2026-06-30";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;
  @Autowired private UserRepository users;
  @Autowired private BookRepository books;
  @Autowired private CategoryRepository categories;
  @Autowired private JdbcTemplate jdbc;

  private static QueryTestData data;

  @BeforeEach
  void seed() {
    if (data == null) data = new QueryTestData(users, books, categories, jdbc);
  }

  // ---------------------------------------------------------------- helpers

  private static String cond(String field, String op, String valueJson) {
    return "{\"kind\":\"condition\",\"field\":\""
        + field
        + "\",\"operator\":\""
        + op
        + "\""
        + (valueJson == null ? "" : ",\"value\":" + valueJson)
        + "}";
  }

  private static String and(String... children) {
    return "{\"kind\":\"group\",\"op\":\"AND\",\"children\":[" + String.join(",", children) + "]}";
  }

  private static String q(String s) {
    return "\"" + s + "\"";
  }

  private static String analyzeBody(String filter, String bucket, String start, String end) {
    return "{\"filter\":"
        + filter
        + ",\"bucket\":\""
        + bucket
        + "\",\"window\":{\"startDate\":\""
        + start
        + "\",\"endDate\":\""
        + end
        + "\"}}";
  }

  private MvcResult call(String subject, UUID book, String path, String json) throws Exception {
    MvcResult r =
        mvc.perform(
                post("/v1/books/" + book + "/transactions/" + path)
                    .with(jwt().jwt(j -> j.subject(subject)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andReturn();
    if (r.getRequest().isAsyncStarted()) r = mvc.perform(asyncDispatch(r)).andReturn();
    return r;
  }

  private JsonNode analyze(UUID book, String json) throws Exception {
    MvcResult r = call(data.subjectA, book, "analyze", json);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return om.readTree(r.getResponse().getContentAsString());
  }

  private JsonNode analyzeError(UUID book, String json, int status, String code) throws Exception {
    MvcResult r = call(data.subjectA, book, "analyze", json);
    String content = r.getResponse().getContentAsString();
    assertEquals(status, r.getResponse().getStatus(), content);
    assertEquals(code, om.readTree(content).get("error").get("code").asText(), content);
    return om.readTree(content);
  }

  private JsonNode search(UUID book, String filter, String sort, int offset, int limit)
      throws Exception {
    String body =
        "{\"filter\":"
            + filter
            + (sort == null ? "" : ",\"sort\":" + sort)
            + ",\"page\":{\"offset\":"
            + offset
            + ",\"limit\":"
            + limit
            + "}}";
    MvcResult r = call(data.subjectA, book, "search", body);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return om.readTree(r.getResponse().getContentAsString());
  }

  /** Every matching row via successive pages of 200. */
  private List<JsonNode> searchAll(UUID book, String filter, String sort) throws Exception {
    List<JsonNode> out = new ArrayList<>();
    for (int offset = 0; ; offset += 200) {
      JsonNode r = search(book, filter, sort, offset, 200);
      r.get("items").forEach(out::add);
      if (!r.get("page").get("hasMore").asBoolean()) return out;
    }
  }

  private static JsonNode cat(JsonNode analysis, UUID id) {
    for (JsonNode c : analysis.get("categories"))
      if (c.get("categoryId").asText().equals(id.toString())) return c;
    throw new AssertionError("category missing in analysis: " + id);
  }

  private static JsonNode bucket(JsonNode analysis, String key) {
    for (JsonNode b : analysis.get("buckets")) if (b.get("key").asText().equals(key)) return b;
    throw new AssertionError("bucket missing: " + key);
  }

  private Sheet exportSheet(MvcResult r) throws Exception {
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    XSSFWorkbook wb =
        new XSSFWorkbook(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()));
    return wb.getSheet("Transactions");
  }

  // ---------------------------------------------------------------- analyze

  @Test
  void fullLedgerTotalsMatchTheHandCalculation() throws Exception {
    JsonNode a = analyze(data.bookA, analyzeBody(and(), "MONTH", FULL_START, FULL_END));
    assertEquals(data.bookA.toString(), a.get("bookId").asText());
    assertEquals("USD", a.get("currencyCode").asText());
    assertEquals(QueryTestData.COUNT_ALL, a.get("matchedCount").asLong());
    assertEquals(QueryTestData.INCOME_ALL, a.get("incomeTotalMinor").asLong());
    assertEquals(QueryTestData.EXPENSE_ALL, a.get("expenseTotalMinor").asLong());
    assertEquals(
        6_759_400, a.get("netMinor").asLong(), "opening balance (1,000,000) is not filtered net");
    assertEquals(FULL_START, a.get("window").get("startDate").asText());
    assertEquals(FULL_END, a.get("window").get("endDate").asText());
    assertEquals(64, a.get("queryFingerprint").asText().length());
    JsonNode eff = a.get("effectiveFilter");
    assertEquals("AND", eff.get("op").asText());
    JsonNode win = eff.get("children").get(eff.get("children").size() - 1);
    assertEquals("occurredOn", win.get("field").asText());
    assertEquals("BETWEEN", win.get("operator").asText());
    assertEquals(FULL_START, win.get("value").get(0).asText());

    assertEquals(2_160_000, cat(a, data.rent).get("totalMinor").asLong());
    assertEquals(18, cat(a, data.rent).get("count").asLong());
    assertEquals(
        162_000, cat(a, data.groceries).get("totalMinor").asLong(), "3 soft-deleted rows excluded");
    assertEquals(36, cat(a, data.groceries).get("count").asLong());
    assertEquals(45_500, cat(a, data.coffee).get("totalMinor").asLong());
    assertEquals(
        23_100,
        cat(a, data.oldGym).get("totalMinor").asLong(),
        "historical (disabled+deleted) category kept");
    assertEquals(9_000_000, cat(a, data.salary).get("totalMinor").asLong());
    assertEquals(150_000, cat(a, data.freelance).get("totalMinor").asLong());
    assertEquals("EXPENSE", cat(a, data.rent).get("type").asText());
    assertEquals(90.35, cat(a, data.rent).get("percentOfExpense").asDouble(), 0.0001);
    assertTrue(cat(a, data.salary).get("percentOfExpense").isNull(), "income has no expense share");
    assertEquals(6, a.get("categories").size());

    JsonNode largest = a.get("largestExpense");
    assertEquals(120_000, largest.get("amountMinor").asLong());
    assertEquals(
        "2026-06-03", largest.get("occurredOn").asText(), "ties resolve to the latest date");
    assertEquals("Rent", largest.get("category").get("name").asText());

    assertTrue(a.get("monthlyBudgets").isNull(), "not a single full month");

    // dense category x bucket matrix, zeros included
    assertEquals(6 * 18, a.get("categoryBuckets").size());
    long rentCells = 0;
    long coffeeMarch = -1;
    long coffeeOther = 0;
    for (JsonNode c : a.get("categoryBuckets")) {
      if (c.get("categoryId").asText().equals(data.rent.toString()))
        rentCells += c.get("totalMinor").asLong();
      if (c.get("categoryId").asText().equals(data.coffee.toString())) {
        if (c.get("bucketKey").asText().equals("2026-03"))
          coffeeMarch = c.get("totalMinor").asLong();
        else coffeeOther += c.get("totalMinor").asLong();
      }
    }
    assertEquals(2_160_000, rentCells);
    assertEquals(45_500, coffeeMarch);
    assertEquals(0, coffeeOther);
  }

  @Test
  void monthlyBucketsSumToYearlyBucketsAndMatchSearchAndSummaryRoute() throws Exception {
    JsonNode monthly = analyze(data.bookA, analyzeBody(and(), "MONTH", FULL_START, FULL_END));
    JsonNode yearly = analyze(data.bookA, analyzeBody(and(), "YEAR", FULL_START, FULL_END));
    assertEquals(18, monthly.get("buckets").size());
    assertEquals(2, yearly.get("buckets").size());

    Map<String, long[]> sums = new HashMap<>();
    for (JsonNode b : monthly.get("buckets")) {
      long[] s = sums.computeIfAbsent(b.get("key").asText().substring(0, 4), k -> new long[3]);
      s[0] += b.get("incomeTotalMinor").asLong();
      s[1] += b.get("expenseTotalMinor").asLong();
      s[2] += b.get("count").asLong();
      assertEquals(
          b.get("incomeTotalMinor").asLong() - b.get("expenseTotalMinor").asLong(),
          b.get("netMinor").asLong());
      assertFalse(b.get("partial").asBoolean(), "whole months inside the window");
    }
    for (JsonNode y : yearly.get("buckets")) {
      long[] s = sums.get(y.get("key").asText());
      assertEquals(s[0], y.get("incomeTotalMinor").asLong());
      assertEquals(s[1], y.get("expenseTotalMinor").asLong());
      assertEquals(s[2], y.get("count").asLong());
    }
    assertEquals(6_100_000, bucket(yearly, "2025").get("incomeTotalMinor").asLong());
    assertEquals(1_571_100, bucket(yearly, "2025").get("expenseTotalMinor").asLong());
    assertEquals(55, bucket(yearly, "2025").get("count").asLong());
    assertEquals(3_050_000, bucket(yearly, "2026").get("incomeTotalMinor").asLong());
    assertEquals(819_500, bucket(yearly, "2026").get("expenseTotalMinor").asLong());
    assertEquals(156, bucket(yearly, "2026").get("count").asLong());
    assertFalse(bucket(yearly, "2025").get("partial").asBoolean());
    assertTrue(bucket(yearly, "2026").get("partial").asBoolean(), "window ends 06-30, not 12-31");
    assertEquals("2026-01-01", bucket(yearly, "2026").get("start").asText());
    assertEquals(FULL_END, bucket(yearly, "2026").get("end").asText());

    // search count == aggregate count when search receives the same window as conditions
    JsonNode s =
        search(
            data.bookA,
            and(cond("occurredOn", "BETWEEN", "[" + q(FULL_START) + "," + q(FULL_END) + "]")),
            null,
            0,
            1);
    assertEquals(monthly.get("matchedCount").asLong(), s.get("totalCount").asLong());
    // the legacy summary route agrees on the same window
    MvcResult legacy =
        mvc.perform(
                get("/v1/books/"
                        + data.bookA
                        + "/summary/range?startDate="
                        + FULL_START
                        + "&endDate="
                        + FULL_END)
                    .with(jwt().jwt(j -> j.subject(data.subjectA))))
            .andReturn();
    assertEquals(200, legacy.getResponse().getStatus(), legacy.getResponse().getContentAsString());
    JsonNode range = om.readTree(legacy.getResponse().getContentAsString());
    assertEquals(monthly.get("incomeTotalMinor").asLong(), range.get("incomeTotalMinor").asLong());
    assertEquals(
        monthly.get("expenseTotalMinor").asLong(), range.get("expenseTotalMinor").asLong());
    assertEquals(monthly.get("matchedCount").asLong(), range.get("transactionCount").asLong());
  }

  @Test
  void partialEdgeMonthsAreLabelledAndInclusiveDateEdgesCount() throws Exception {
    // Mar 15..Apr 30: March is clipped; the freelance row on Apr 30 (last day) is included.
    JsonNode a = analyze(data.bookA, analyzeBody(and(), "MONTH", "2025-03-15", "2025-04-30"));
    JsonNode mar = bucket(a, "2025-03");
    JsonNode apr = bucket(a, "2025-04");
    assertTrue(mar.get("partial").asBoolean());
    assertEquals("2025-03-15", mar.get("start").asText());
    assertEquals("2025-03-31", mar.get("end").asText());
    assertEquals(4_500, mar.get("expenseTotalMinor").asLong());
    assertEquals(1, mar.get("count").asLong());
    assertFalse(apr.get("partial").asBoolean());
    assertEquals(525_000, apr.get("incomeTotalMinor").asLong());
    assertEquals(129_000, apr.get("expenseTotalMinor").asLong());
    assertEquals(5, apr.get("count").asLong());
    assertEquals(6, a.get("matchedCount").asLong());
    assertEquals(391_500, a.get("netMinor").asLong());

    // one-day window on a boundary
    JsonNode day = analyze(data.bookA, analyzeBody(and(), "DAY", "2025-04-30", "2025-04-30"));
    assertEquals(1, day.get("buckets").size());
    assertEquals(25_000, day.get("incomeTotalMinor").asLong());
  }

  @Test
  void everyCategoryDrillDownReconcilesWithItsAggregate() throws Exception {
    String window = cond("occurredOn", "BETWEEN", "[" + q(FULL_START) + "," + q(FULL_END) + "]");
    JsonNode a = analyze(data.bookA, analyzeBody(and(), "MONTH", FULL_START, FULL_END));
    long grand = 0;
    for (JsonNode c : a.get("categories")) {
      String catFilter = and(cond("categoryId", "EQ", q(c.get("categoryId").asText())), window);
      List<JsonNode> rows = searchAll(data.bookA, catFilter, null);
      assertEquals(c.get("count").asLong(), rows.size(), c.get("categoryName").asText());
      assertEquals(
          c.get("totalMinor").asLong(),
          rows.stream().mapToLong(r -> r.get("amountMinor").asLong()).sum());
      grand += rows.size();
    }
    assertEquals(a.get("matchedCount").asLong(), grand);

    // a narrowing filter: analysis, search and export all agree on the same expression
    String filter = and(cond("paymentMethod", "IS_NULL", null));
    JsonNode nulls = analyze(data.bookA, analyzeBody(filter, "YEAR", FULL_START, FULL_END));
    assertEquals(101, nulls.get("matchedCount").asLong());
    List<JsonNode> rows =
        searchAll(data.bookA, and(cond("paymentMethod", "IS_NULL", null), window), null);
    assertEquals(101, rows.size());
    MvcResult ex =
        call(
            data.subjectA,
            data.bookA,
            "export/query",
            "{\"filter\":" + and(cond("paymentMethod", "IS_NULL", null)) + "}");
    Sheet sheet = exportSheet(ex);
    assertEquals(101, sheet.getLastRowNum());
  }

  @Test
  void budgetsAppearOnlyForAFullCalendarMonthAndAreLabelled() throws Exception {
    JsonNode a =
        analyze(
            data.bookA,
            analyzeBody(and(cond("type", "EQ", q("EXPENSE"))), "DAY", "2025-04-01", "2025-04-30"));
    JsonNode b = a.get("monthlyBudgets");
    assertEquals("2025-04", b.get("month").asText());
    assertTrue(b.get("label").asText().contains("Not adjusted for the applied filter"));
    assertEquals(2, b.get("items").size());
    long total = 0;
    for (JsonNode i : b.get("items")) total += i.get("amountMinor").asLong();
    assertEquals(160_000, total);
    assertEquals(93.02, cat(a, data.rent).get("percentOfExpense").asDouble(), 0.0001);
    assertEquals(6.98, cat(a, data.groceries).get("percentOfExpense").asDouble(), 0.0001);
    assertEquals(30, a.get("buckets").size());

    assertTrue(
        analyze(data.bookA, analyzeBody(and(), "DAY", "2025-04-01", "2025-04-29"))
            .get("monthlyBudgets")
            .isNull());
    assertTrue(
        analyze(data.bookA, analyzeBody(and(), "DAY", "2025-04-02", "2025-04-30"))
            .get("monthlyBudgets")
            .isNull());
  }

  @Test
  void emptyResultsRenderZerosNoPercentagesAndNoLargestExpense() throws Exception {
    JsonNode none =
        analyze(
            data.bookA,
            analyzeBody(
                and(cond("title", "EQ", q("no-such-title"))), "MONTH", "2025-01-01", "2025-03-31"));
    assertEquals(0, none.get("matchedCount").asLong());
    assertEquals(0, none.get("netMinor").asLong());
    assertEquals(0, none.get("categories").size());
    assertEquals(0, none.get("categoryBuckets").size());
    assertEquals(3, none.get("buckets").size(), "zero buckets still cover the requested period");
    assertTrue(none.get("largestExpense").isNull());

    JsonNode income =
        analyze(
            data.bookA,
            analyzeBody(and(cond("type", "EQ", q("INCOME"))), "YEAR", FULL_START, FULL_END));
    assertEquals(0, income.get("expenseTotalMinor").asLong());
    for (JsonNode c : income.get("categories"))
      assertTrue(c.get("percentOfExpense").isNull(), "zero denominator");
    assertTrue(income.get("largestExpense").isNull());
  }

  @Test
  void oversizedCategoryByBucketOutputIsRejectedNotTruncated() throws Exception {
    UUID wide = data.newBookForA("wide");
    for (int i = 0; i < 12; i++) {
      UUID c = data.newCategoryFor(wide, CategoryType.EXPENSE, "Cat " + i);
      data.tx(wide, c, "EXPENSE", 100, LocalDate.of(2025, 1, 1), "t", null, null, null);
    }
    // 12 categories x 1826 days = 21,912 cells > 20,000
    JsonNode err =
        analyzeError(
            wide, analyzeBody(and(), "DAY", "2021-01-01", "2025-12-31"), 422, "ANALYSIS_TOO_LARGE");
    assertTrue(err.get("error").get("message").asText().contains("Narrow"));
    // the same data with a coarser bucket is fine
    assertEquals(
        12,
        analyze(wide, analyzeBody(and(), "MONTH", "2021-01-01", "2025-12-31"))
            .get("categories")
            .size());
  }

  @Test
  void aggregatesStayJsExactAndOverflowIsRejected() throws Exception {
    UUID ok = data.newBookForA("big-ok");
    UUID over = data.newBookForA("big-over");
    UUID okCat = data.newCategoryFor(ok, CategoryType.EXPENSE, "Big");
    UUID overCat = data.newCategoryFor(over, CategoryType.EXPENSE, "Big");
    for (int i = 0; i < 9; i++)
      data.tx(
          ok,
          okCat,
          "EXPENSE",
          999_999_999_999_999L,
          LocalDate.of(2025, 1, 1 + i),
          "b",
          null,
          null,
          null);
    for (int i = 0; i < 10; i++)
      data.tx(
          over,
          overCat,
          "EXPENSE",
          999_999_999_999_999L,
          LocalDate.of(2025, 1, 1 + i),
          "b",
          null,
          null,
          null);

    JsonNode a = analyze(ok, analyzeBody(and(), "YEAR", "2025-01-01", "2025-12-31"));
    assertEquals(
        "8999999999999991", a.get("expenseTotalMinor").asText(), "exact integer, not a float");
    assertEquals("-8999999999999991", a.get("netMinor").asText());
    analyzeError(
        over,
        analyzeBody(and(), "YEAR", "2025-01-01", "2025-12-31"),
        422,
        "AMOUNT_TOTAL_UNSUPPORTED");
  }

  @Test
  void analyzeValidationAndTenancy() throws Exception {
    analyzeError(
        data.bookA, "{\"filter\":" + and() + ",\"bucket\":\"MONTH\"}", 400, "VALIDATION_ERROR");
    analyzeError(
        data.bookA, analyzeBody(and(), "WEEK", FULL_START, FULL_END), 400, "VALIDATION_ERROR");
    analyzeError(
        data.bookA, analyzeBody(and(), "DAY", "2025-02-01", "2025-01-01"), 400, "VALIDATION_ERROR");
    analyzeError(
        data.bookA,
        analyzeBody(and(), "MONTH", "2015-01-01", "2025-12-31"),
        400,
        "VALIDATION_ERROR");
    // 29 user conditions + the window condition = 30: ok; 30 + window = 31: rejected
    List<String> conds = new ArrayList<>();
    for (int i = 0; i < 29; i++) conds.add(cond("amountMinor", "GTE", "0"));
    analyze(
        data.bookA, analyzeBody(and(conds.toArray(String[]::new)), "YEAR", FULL_START, FULL_END));
    conds.add(cond("amountMinor", "GTE", "0"));
    analyzeError(
        data.bookA,
        analyzeBody(and(conds.toArray(String[]::new)), "YEAR", FULL_START, FULL_END),
        400,
        "VALIDATION_ERROR");

    MvcResult other =
        call(
            data.subjectB, data.bookA, "analyze", analyzeBody(and(), "YEAR", FULL_START, FULL_END));
    assertEquals(404, other.getResponse().getStatus());
    MvcResult b =
        call(
            data.subjectB,
            data.bookB,
            "analyze",
            analyzeBody(and(), "YEAR", "2025-01-01", "2025-12-31"));
    JsonNode bb = om.readTree(b.getResponse().getContentAsString());
    assertEquals(5, bb.get("matchedCount").asLong());
    assertEquals(555_555, bb.get("expenseTotalMinor").asLong());
  }

  // ---------------------------------------------------------------- export

  @Test
  void queryExportContainsExactlyTheSearchRowsInTheSameOrder() throws Exception {
    String sort =
        "[{\"field\":\"amountMinor\",\"direction\":\"DESC\"},{\"field\":\"occurredOn\",\"direction\":\"ASC\"}]";
    String filter = and(cond("type", "EQ", q("EXPENSE")));
    List<JsonNode> expected = searchAll(data.bookA, filter, sort);
    assertEquals(187, expected.size(), "54 monthly + 3 gym + 130 coffee expenses");

    MvcResult r =
        call(
            data.subjectA,
            data.bookA,
            "export/query",
            "{\"filter\":" + filter + ",\"sort\":" + sort + "}");
    Sheet sheet = exportSheet(r);
    assertTrue(r.getResponse().getContentType().contains("spreadsheetml"));
    assertTrue(r.getResponse().getHeader("Content-Disposition").contains("filtered.xlsx"));
    assertEquals(
        expected.size(), sheet.getLastRowNum(), "header + every matching row, not one page");
    for (int i = 0; i < expected.size(); i++) {
      Row row = sheet.getRow(i + 1);
      assertEquals(
          expected.get(i).get("id").asText(), row.getCell(9).getStringCellValue(), "row " + i);
    }
    assertEquals("Amount", sheet.getRow(0).getCell(3).getStringCellValue());
    assertEquals(0, tmpExportFilesCount(), "temp file deleted after streaming");
  }

  @Test
  void queryExportWritesLiteralStringCells() throws Exception {
    UUID book = data.newBookForA("literal");
    UUID c = data.newCategoryFor(book, CategoryType.EXPENSE, "Misc");
    data.tx(book, c, "EXPENSE", 100, LocalDate.of(2025, 1, 1), "=1+1", "@SUM(A1:A2)", null, null);
    Sheet sheet = exportSheet(call(data.subjectA, book, "export/query", "{}"));
    Row row = sheet.getRow(1);
    assertEquals(CellType.STRING, row.getCell(2).getCellType());
    assertEquals("=1+1", row.getCell(2).getStringCellValue());
    assertEquals(CellType.STRING, row.getCell(7).getCellType());
    assertEquals("@SUM(A1:A2)", row.getCell(7).getStringCellValue());
  }

  @Test
  void queryExportReadsInChunksAndEnforcesTheRowLimit() throws Exception {
    UUID chunky = data.newBookForA("chunky");
    UUID cc = data.newCategoryFor(chunky, CategoryType.EXPENSE, "Misc");
    insertMany(chunky, cc, 2_500);
    Sheet sheet = exportSheet(call(data.subjectA, chunky, "export/query", "{}"));
    assertEquals(2_500, sheet.getLastRowNum());
    Map<String, Integer> ids = new HashMap<>();
    for (int i = 1; i <= 2_500; i++)
      ids.merge(sheet.getRow(i).getCell(9).getStringCellValue(), 1, Integer::sum);
    assertEquals(2_500, ids.size(), "no duplicate or skipped rows across chunk boundaries");

    UUID huge = data.newBookForA("huge");
    UUID hc = data.newCategoryFor(huge, CategoryType.EXPENSE, "Misc");
    insertMany(huge, hc, FilteredExportService.MAX_ROWS + 1);
    MvcResult r = call(data.subjectA, huge, "export/query", "{}");
    assertEquals(422, r.getResponse().getStatus());
    JsonNode err = om.readTree(r.getResponse().getContentAsString()).get("error");
    assertEquals("EXPORT_TOO_LARGE", err.get("code").asText());
    assertTrue(err.get("message").asText().contains("Narrow the filter"));
    assertEquals(0, tmpExportFilesCount(), "no temp file left behind after a rejected export");
  }

  @Test
  void queryExportIsOwnershipCheckedAndLegacyExportStillWorks() throws Exception {
    assertEquals(
        404, call(data.subjectB, data.bookA, "export/query", "{}").getResponse().getStatus());
    assertEquals(
        400,
        call(data.subjectA, data.bookA, "export/query", "{\"page\":{}}").getResponse().getStatus());

    MvcResult legacy =
        mvc.perform(
                get("/v1/books/" + data.bookA + "/transactions/export")
                    .with(jwt().jwt(j -> j.subject(data.subjectA))))
            .andReturn();
    if (legacy.getRequest().isAsyncStarted())
      legacy = mvc.perform(asyncDispatch(legacy)).andReturn();
    assertEquals((int) QueryTestData.COUNT_ALL, exportSheet(legacy).getLastRowNum());
  }

  // ---------------------------------------------------------------- utilities

  private void insertMany(UUID book, UUID category, int n) {
    jdbc.update(
        "insert into expense_tracker.transactions (id, book_id, type, amount_minor, occurred_on,"
            + " category_id, created_at, updated_at, version) select gen_random_uuid(), ?,"
            + " 'EXPENSE', 100 + g, date '2025-01-01' + (g % 300), ?, now(), now(), 0 from"
            + " generate_series(1, ?) g",
        book, category, n);
  }

  private static Path tmpExportFiles() {
    return Path.of(System.getProperty("java.io.tmpdir"));
  }

  private static long tmpExportFilesCount() throws Exception {
    try (Stream<Path> s = Files.list(tmpExportFiles())) {
      return s.filter(p -> p.getFileName().toString().startsWith("pennywise-export-")).count();
    }
  }
}
