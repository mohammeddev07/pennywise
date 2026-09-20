package com.axel.pennywise.domain.transaction.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.axel.pennywise.domain.book.BookRepository;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

/** P1.2: POST /v1/books/{bookId}/transactions/search against real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "app.security.auth-enabled=true",
      "spring.jpa.properties.hibernate.session_factory.statement_inspector="
          + "com.axel.pennywise.domain.transaction.query.SqlCapture"
    })
class TransactionSearchIT extends AbstractPostgresIT {

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

  private static String group(String op, String... children) {
    return "{\"kind\":\"group\",\"op\":\""
        + op
        + "\",\"children\":["
        + String.join(",", children)
        + "]}";
  }

  private static String q(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static String body(String filter, String sort, String page) {
    return "{\"filter\":"
        + filter
        + (sort == null ? "" : ",\"sort\":" + sort)
        + (page == null ? "" : ",\"page\":" + page)
        + "}";
  }

  private MvcResult search(String subject, UUID book, String json) throws Exception {
    return mvc.perform(
            post("/v1/books/" + book + "/transactions/search")
                .with(jwt().jwt(j -> j.subject(subject)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andReturn();
  }

  private JsonNode ok(UUID book, String json) throws Exception {
    MvcResult r = search(data.subjectA, book, json);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return om.readTree(r.getResponse().getContentAsString());
  }

  private long total(UUID book, String filter) throws Exception {
    return ok(book, body(filter, null, "{\"limit\":1}")).get("totalCount").asLong();
  }

  private long totalA(String filter) throws Exception {
    return total(data.bookA, filter);
  }

  private JsonNode error(String subject, UUID book, String json, int status, String code)
      throws Exception {
    MvcResult r = search(subject, book, json);
    String content = r.getResponse().getContentAsString();
    assertEquals(status, r.getResponse().getStatus(), content);
    JsonNode err = om.readTree(content).get("error");
    assertEquals(code, err.get("code").asText(), content);
    return err;
  }

  private static List<String> ids(JsonNode resp) {
    List<String> out = new ArrayList<>();
    resp.get("items").forEach(i -> out.add(i.get("id").asText()));
    return out;
  }

  private static String eqCategory(UUID id) {
    return cond("categoryId", "EQ", q(id.toString()));
  }

  // ---------------------------------------------------------------- tests

  @Test
  void emptyTopLevelAndReturnsEveryActiveRowOfTheOwnedBookOnly() throws Exception {
    JsonNode r = ok(data.bookA, body(group("AND"), null, null));
    assertEquals(
        QueryTestData.COUNT_ALL, r.get("totalCount").asLong(), "deleted rows are excluded");
    assertEquals(50, r.get("items").size(), "default limit");
    assertTrue(r.get("page").get("hasMore").asBoolean());
    assertEquals(0, r.get("page").get("offset").asInt());
    assertEquals(64, r.get("queryFingerprint").asText().length());
    r.get("items").forEach(i -> assertEquals(data.bookA.toString(), i.get("bookId").asText()));

    // filter omitted entirely behaves the same
    assertEquals(QueryTestData.COUNT_ALL, ok(data.bookA, "{}").get("totalCount").asLong());
  }

  @Test
  void nestedPrecedenceAndOrIsRespected() throws Exception {
    String income = cond("type", "EQ", q("INCOME"));
    String freelance = eqCategory(data.freelance);
    String rent = eqCategory(data.rent);
    // A AND (B OR C): only freelance is income -> 6
    assertEquals(6, totalA(group("AND", income, group("OR", freelance, rent))));
    // (A AND B) OR C: 6 freelance + 18 rent
    assertEquals(24, totalA(group("OR", group("AND", income, freelance), rent)));
    // 3 levels deep
    assertEquals(
        24, totalA(group("AND", group("OR", group("AND", income, freelance), group("AND", rent)))));
  }

  @Test
  void multipleConditionsOnOneFieldCombine() throws Exception {
    // 4500 <= amount <= 7700 and not 5000: 36 groceries + 3 gym
    assertEquals(
        39,
        totalA(
            group(
                "AND",
                cond("amountMinor", "GTE", "4500"),
                cond("amountMinor", "LTE", "7700"),
                cond("amountMinor", "NE", "5000"))));
    assertEquals(3, totalA(cond("amountMinor", "BETWEEN", "[7700,7700]")), "BETWEEN is inclusive");
    // two IN-style conditions on one field ANDed
    assertEquals(
        36,
        totalA(
            group(
                "AND",
                cond(
                    "categoryId",
                    "IN",
                    "[" + q(data.groceries.toString()) + "," + q(data.rent.toString()) + "]"),
                cond("categoryId", "NOT_IN", "[" + q(data.rent.toString()) + "]"))));
  }

  @Test
  void nullSemanticsAndNullsLastInBothDirections() throws Exception {
    assertEquals(101, totalA(cond("paymentMethod", "IS_NULL", null)));
    assertEquals(110, totalA(cond("paymentMethod", "IS_NOT_NULL", null)));
    assertEquals(83, totalA(cond("paymentMethod", "EQ", q("CARD"))));
    // NE excludes nulls (27 = BANK_TRANSFER 18 + WALLET 6 + CASH 3); users OR IS_NULL to include
    assertEquals(27, totalA(cond("paymentMethod", "NE", q("CARD"))));
    assertEquals(
        128,
        totalA(
            group(
                "OR",
                cond("paymentMethod", "NE", q("CARD")),
                cond("paymentMethod", "IS_NULL", null))));
    assertEquals(3, totalA(cond("occurredAt", "IS_NULL", null)), "gym rows have no occurredAt");

    for (String dir : new String[] {"ASC", "DESC"}) {
      JsonNode r =
          ok(
              data.bookA,
              body(
                  group("AND"),
                  "[{\"field\":\"paymentMethod\",\"direction\":\"" + dir + "\"}]",
                  "{\"limit\":200}"));
      JsonNode items = r.get("items");
      for (int i = 0; i < 110; i++)
        assertFalse(items.get(i).get("paymentMethod").isNull(), dir + " #" + i);
      assertTrue(items.get(110).get("paymentMethod").isNull(), dir + ": nulls last");
    }
  }

  @Test
  void mixedSortDirectionsAndDeterministicDefault() throws Exception {
    JsonNode r =
        ok(
            data.bookA,
            body(
                group("OR", eqCategory(data.groceries), eqCategory(data.rent)),
                "[{\"field\":\"occurredOn\",\"direction\":\"DESC\"},{\"field\":\"amountMinor\",\"direction\":\"ASC\"}]",
                "{\"limit\":200}"));
    JsonNode items = r.get("items");
    assertEquals(54, items.size());
    for (int i = 1; i < items.size(); i++) {
      String d0 = items.get(i - 1).get("occurredOn").asText();
      String d1 = items.get(i).get("occurredOn").asText();
      assertTrue(d0.compareTo(d1) >= 0, "date DESC at " + i);
      if (d0.equals(d1)) {
        assertTrue(
            items.get(i - 1).get("amountMinor").asLong()
                <= items.get(i).get("amountMinor").asLong());
      }
    }
    // default sort is occurredOn DESC, createdAt DESC, id DESC
    JsonNode def = ok(data.bookA, "{}").get("items");
    assertEquals("2026-06-20", def.get(0).get("occurredOn").asText());
  }

  @Test
  void equalValueTiesNeverDuplicateOrSkipRowsAcrossPages() throws Exception {
    for (String sort :
        new String[] {
          "[{\"field\":\"amountMinor\",\"direction\":\"ASC\"}]", // all 130 rows tie
          "[{\"field\":\"occurredOn\",\"direction\":\"DESC\"}]", // 5 rows per day tie
          null
        }) {
      Set<String> seen = new HashSet<>();
      List<String> order = new ArrayList<>();
      for (int offset = 0; offset < 130; offset += 50) {
        JsonNode r =
            ok(
                data.bookA,
                body(eqCategory(data.coffee), sort, "{\"offset\":" + offset + ",\"limit\":50}"));
        assertEquals(130, r.get("totalCount").asLong());
        assertEquals(offset + 50 < 130, r.get("page").get("hasMore").asBoolean());
        for (String id : ids(r)) {
          assertTrue(seen.add(id), "duplicate row across pages: " + id);
          order.add(id);
        }
      }
      assertEquals(130, seen.size(), "sort=" + sort);
      if (sort != null && sort.contains("amountMinor")) {
        List<String> sorted = new ArrayList<>(order);
        sorted.sort(String::compareTo);
        assertEquals(sorted, order, "id ASC is appended as the tie-breaker");
      }
    }
  }

  @Test
  void moreThanOneHundredMatchesPageThroughToTheEnd() throws Exception {
    JsonNode last =
        ok(data.bookA, body(eqCategory(data.coffee), null, "{\"offset\":100,\"limit\":50}"));
    assertEquals(30, last.get("items").size());
    assertFalse(last.get("page").get("hasMore").asBoolean());
    JsonNode beyond =
        ok(data.bookA, body(eqCategory(data.coffee), null, "{\"offset\":5000,\"limit\":50}"));
    assertEquals(0, beyond.get("items").size());
    assertEquals(130, beyond.get("totalCount").asLong());
  }

  @Test
  void textMatchingIsLiteralAndCaseInsensitive() throws Exception {
    UUID t = data.bookText;
    assertEquals(1, total(t, cond("title", "CONTAINS", q("%"))), "% is not a wildcard");
    assertEquals(2, total(t, cond("description", "CONTAINS", q("%"))), "title OR note");
    assertEquals(1, total(t, cond("title", "CONTAINS", q("_"))), "_ is not a wildcard");
    assertEquals(1, total(t, cond("title", "CONTAINS", q("\\"))), "backslash is literal");
    assertEquals(2, total(t, cond("title", "STARTS_WITH", q("PATH"))));
    assertEquals(2, total(t, cond("title", "ENDS_WITH", q("DIR"))));
    assertEquals(1, total(t, cond("title", "EQ", q("upper case"))));
    assertEquals(0, total(t, cond("title", "EQ", q("100%"))), "no wildcard in EQ either");
    // NOT_CONTAINS on a concrete field excludes null titles: 7 non-null titles - 2 organic
    assertEquals(5, total(t, cond("title", "NOT_CONTAINS", q("organic"))));
    // description negation is the logical negation over coalesced '' : includes note-only and empty
    // rows
    assertEquals(7, total(t, cond("description", "NOT_CONTAINS", q("organic"))));
    assertEquals(1, total(t, cond("note", "CONTAINS", q("percent 50%"))));
  }

  @Test
  void everyFieldSortsAscAndDescAndTextSortIsCaseNormalized() throws Exception {
    for (TxField f : TxField.values()) {
      for (String dir : new String[] {"ASC", "DESC"}) {
        String sort = "[{\"field\":\"" + f.wireName() + "\",\"direction\":\"" + dir + "\"}]";
        JsonNode r = ok(data.bookA, body(group("AND"), sort, "{\"limit\":10}"));
        assertEquals(10, r.get("items").size(), f + " " + dir);
      }
    }
    for (String dir : new String[] {"ASC", "DESC"}) {
      JsonNode items =
          ok(
                  data.bookText,
                  body(group("AND"), "[{\"field\":\"title\",\"direction\":\"" + dir + "\"}]", null))
              .get("items");
      int first = dir.equals("ASC") ? 0 : 6; // 7 non-null titles, then 2 nulls last
      assertEquals(
          "UPPER Case",
          items.get(dir.equals("ASC") ? 6 : 0).get("title").asText(),
          "lower() ordering, " + dir);
      assertFalse(items.get(first).get("title").isNull());
      assertTrue(items.get(7).get("title").isNull());
      assertTrue(items.get(8).get("title").isNull(), "NULLS LAST in " + dir);
    }
  }

  @Test
  void historicalCategoryOfAnActiveRowStillResolves() throws Exception {
    JsonNode r = ok(data.bookA, body(cond("categoryName", "EQ", q("old gym")), null, null));
    assertEquals(3, r.get("totalCount").asLong());
    assertEquals("Old Gym", r.get("items").get(0).get("category").get("name").asText());
    assertEquals(3, totalA(eqCategory(data.oldGym)));
  }

  @Test
  void softDeletedRowsNeverMatch() throws Exception {
    assertEquals(0, totalA(cond("title", "EQ", q("Deleted"))));
    assertEquals(0, totalA(cond("amountMinor", "EQ", "999999")));
    assertEquals(
        0,
        totalA(
            group("OR", cond("amountMinor", "EQ", "999999"), cond("title", "EQ", q("Deleted")))));
  }

  @Test
  void otherTenantsAndUnknownBooksAreNotFound() throws Exception {
    error(data.subjectB, data.bookA, "{}", 404, "NOT_FOUND");
    error(data.subjectA, data.bookB, "{}", 404, "NOT_FOUND");
    error(data.subjectA, UUID.randomUUID(), "{}", 404, "NOT_FOUND");
    // a foreign category id inside my book matches nothing and leaks nothing
    assertEquals(0, totalA(eqCategory(data.bCategory)));
    // an OR group cannot widen scope to another tenant's rows
    assertEquals(
        QueryTestData.COUNT_ALL,
        totalA(group("OR", cond("amountMinor", "GTE", "0"), cond("amountMinor", "EQ", "111111"))));
    MvcResult own = search(data.subjectB, data.bookB, "{}");
    JsonNode b = om.readTree(own.getResponse().getContentAsString());
    assertEquals(5, b.get("totalCount").asLong());
    b.get("items").forEach(i -> assertEquals(data.bookB.toString(), i.get("bookId").asText()));
  }

  @Test
  void unauthenticatedRequestsAreRejected() throws Exception {
    MvcResult r =
        mvc.perform(
                post("/v1/books/" + data.bookA + "/transactions/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andReturn();
    assertEquals(401, r.getResponse().getStatus());
  }

  @Test
  void invalidAndOversizedRequestsGetClearErrors() throws Exception {
    JsonNode e =
        error(
            data.subjectA,
            data.bookA,
            body(cond("bogus", "EQ", "1"), null, null),
            400,
            "VALIDATION_ERROR");
    assertEquals("filter.field", e.get("details").get(0).get("path").asText());
    error(
        data.subjectA,
        data.bookA,
        body(cond("title", "BETWEEN", "[\"a\",\"b\"]"), null, null),
        400,
        "VALIDATION_ERROR");
    error(
        data.subjectA,
        data.bookA,
        body(cond("categoryId", "EQ", q("not-a-uuid")), null, null),
        400,
        "VALIDATION_ERROR");
    error(
        data.subjectA,
        data.bookA,
        body(cond("amountMinor", "BETWEEN", "[10,5]"), null, null),
        400,
        "VALIDATION_ERROR");
    error(data.subjectA, data.bookA, body(group("OR"), null, null), 400, "VALIDATION_ERROR");
    error(data.subjectA, data.bookA, "{\"filter\":", 400, "BAD_REQUEST");
    error(
        data.subjectA,
        data.bookA,
        body(group("AND"), null, "{\"limit\":201}"),
        400,
        "VALIDATION_ERROR");
    error(
        data.subjectA,
        data.bookA,
        body(group("AND"), null, "{\"offset\":100001}"),
        400,
        "PAGE_OFFSET_LIMIT_EXCEEDED");
    // offset 100000 is allowed (and simply empty here)
    assertEquals(
        0, ok(data.bookA, body(group("AND"), null, "{\"offset\":100000}")).get("items").size());

    String huge =
        "{\"filter\":"
            + cond("title", "CONTAINS", q("x".repeat(QueryRequestParser.MAX_BODY_BYTES)))
            + "}";
    error(data.subjectA, data.bookA, huge, 413, "REQUEST_TOO_LARGE");
  }

  @Test
  void injectionPayloadsAreBoundNotConcatenated() throws Exception {
    String payload = "x'); DROP TABLE expense_tracker.transactions; --";
    SqlCapture.STATEMENTS.clear();
    assertEquals(0, totalA(cond("title", "CONTAINS", q(payload))));
    assertEquals(0, totalA(cond("title", "EQ", q("' OR '1'='1"))));
    assertTrue(SqlCapture.STATEMENTS.size() >= 2);
    for (String sql : SqlCapture.STATEMENTS) {
      assertFalse(sql.toLowerCase().contains("drop table"), sql);
      assertFalse(sql.contains("1'='1"), sql);
      assertTrue(sql.contains("?"), "values are bind parameters: " + sql);
    }
    assertEquals(QueryTestData.COUNT_ALL, totalA(group("AND")), "table is intact");
  }

  @Test
  void aPageIsOneCountAndOneSelectWithCategoryFetched() throws Exception {
    ok(data.bookA, "{}"); // warm up
    SqlCapture.STATEMENTS.clear();
    ok(data.bookA, body(group("AND"), null, "{\"limit\":50}"));
    List<String> reads =
        SqlCapture.STATEMENTS.stream().filter(s -> s.toLowerCase().startsWith("select")).toList();
    long userAndBookLookups =
        reads.stream()
            .filter(s -> s.contains("expense_tracker.users") || s.contains("expense_tracker.books"))
            .count();
    assertEquals(
        2,
        reads.size() - userAndBookLookups,
        "count + page, no per-row category queries: " + reads);
  }

  @Test
  void fingerprintTracksFilterAndSortButNotThePage() throws Exception {
    String f = cond("title", "CONTAINS", q("coffee"));
    String a =
        ok(data.bookA, body(f, null, "{\"offset\":0,\"limit\":10}"))
            .get("queryFingerprint")
            .asText();
    String b =
        ok(data.bookA, body(f, null, "{\"offset\":50,\"limit\":20}"))
            .get("queryFingerprint")
            .asText();
    String c =
        ok(data.bookA, body(f, "[{\"field\":\"title\",\"direction\":\"ASC\"}]", null))
            .get("queryFingerprint")
            .asText();
    String d =
        ok(data.bookA, body(cond("title", "CONTAINS", q("coffee2")), null, null))
            .get("queryFingerprint")
            .asText();
    assertEquals(a, b);
    assertNotEquals(a, c);
    assertNotEquals(a, d);
  }

  @Test
  void legacyGetListStillWorks() throws Exception {
    MvcResult r =
        mvc.perform(
                get("/v1/books/" + data.bookA + "/transactions?limit=5")
                    .with(jwt().jwt(j -> j.subject(data.subjectA))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    JsonNode page = om.readTree(r.getResponse().getContentAsString());
    assertFalse(page.toString().isEmpty());
  }
}
