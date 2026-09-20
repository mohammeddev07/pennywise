package com.axel.pennywise.domain.transaction.query;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axel.pennywise.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueryRequestParserTest {

  private final QueryRequestParser parser = new QueryRequestParser();

  private static String cond(String field, String op, String value) {
    return "{\"kind\":\"condition\",\"field\":\""
        + field
        + "\",\"operator\":\""
        + op
        + "\""
        + (value == null ? "" : ",\"value\":" + value)
        + "}";
  }

  private static String group(String op, String... kids) {
    return "{\"kind\":\"group\",\"op\":\""
        + op
        + "\",\"children\":["
        + String.join(",", kids)
        + "]}";
  }

  private QuerySpec.Search search(String filter) {
    return parser.parseSearch(("{\"filter\":" + filter + "}").getBytes(StandardCharsets.UTF_8));
  }

  private QuerySpec.Search searchRaw(String json) {
    return parser.parseSearch(json.getBytes(StandardCharsets.UTF_8));
  }

  /** Asserts a 400 whose first detail points at {@code path}. */
  private void rejects(String path, String json) {
    ApiException e = assertThrows(ApiException.class, () -> searchRaw(json), json);
    assertEquals(400, e.status().value(), json);
    assertEquals(path, e.details().get(0).get("path"), json + " -> " + e.getMessage());
  }

  private void rejectsFilter(String path, String filter) {
    rejects(path, "{\"filter\":" + filter + "}");
  }

  // ------------------------------------------------------------------ capability matrix

  private static String sample(TxField f, FilterOperator op) {
    String v =
        switch (f.kind()) {
          case UUID -> "\"" + UUID.randomUUID() + "\"";
          case ENUM -> f == TxField.TYPE ? "\"INCOME\"" : "\"CARD\"";
          case NUMBER -> "5";
          case DATE -> "\"2026-01-31\"";
          case TIMESTAMP -> "\"2026-01-31T10:00:00Z\"";
          case TEXT -> "\"abc\"";
        };
    return switch (op) {
      case IS_NULL, IS_NOT_NULL -> null;
      case IN, NOT_IN -> "[" + v + "]";
      case BETWEEN -> "[" + v + "," + v + "]";
      default -> v;
    };
  }

  @Test
  void everyFieldOperatorPairIsAcceptedExactlyWhenTheMatrixAllowsIt() {
    for (TxField f : TxField.values()) {
      for (FilterOperator op : FilterOperator.values()) {
        String c = cond(f.wireName(), op.name(), sample(f, op));
        if (f.operators().contains(op)) {
          assertDoesNotThrow(() -> search(c), f + " " + op);
        } else {
          rejectsFilter("filter.operator", c);
        }
      }
    }
  }

  @Test
  void matrixMatchesDocumentedRows() throws Exception {
    java.nio.file.Path doc = java.nio.file.Path.of("docs", "transaction-query.md");
    String text = java.nio.file.Files.readString(doc);
    for (TxField f : TxField.values()) {
      String ops =
          f.operators().stream()
              .map(Enum::name)
              .sorted()
              .collect(java.util.stream.Collectors.joining(", "));
      String row =
          "| `"
              + f.wireName()
              + "` | "
              + f.kind()
              + " | "
              + (f.nullable() ? "yes" : "no")
              + " | "
              + ops
              + " |";
      assertTrue(text.contains(row), "docs/transaction-query.md is missing or stale for: " + row);
    }
  }

  @Test
  void excludedFieldsAreRejected() {
    for (String f : new String[] {"bookId", "deletedAt", "version"}) {
      rejectsFilter("filter.field", cond(f, "EQ", "1"));
    }
  }

  // ------------------------------------------------------------------ structure & limits

  @Test
  void emptyTopLevelAndMeansAllButOtherEmptyGroupsAreRejected() {
    assertTrue(((FilterNode.Group) search(group("AND")).query().filter()).children().isEmpty());
    assertTrue(((FilterNode.Group) searchRaw("{}").query().filter()).children().isEmpty());
    rejectsFilter("filter.children", group("OR"));
    rejectsFilter(
        "filter.children[1].children", group("AND", cond("title", "EQ", "\"a\""), group("AND")));
    rejectsFilter("filter.children[0].children", group("AND", group("OR")));
  }

  @Test
  void topLevelConditionIsAllowed() {
    assertTrue(
        search(cond("title", "EQ", "\"a\"")).query().filter() instanceof FilterNode.Condition);
  }

  @Test
  void nestingIsCappedAtThreeLevels() {
    String c = cond("title", "EQ", "\"a\"");
    assertDoesNotThrow(() -> search(group("AND", group("OR", group("AND", c)))));
    rejectsFilter(
        "filter.children[0].children[0].children[0]",
        group("AND", group("OR", group("AND", group("OR", c)))));
  }

  @Test
  void conditionCountIsCappedAtThirty() {
    List<String> conds = new ArrayList<>();
    for (int i = 0; i < 30; i++) conds.add(cond("amountMinor", "GTE", "1"));
    assertDoesNotThrow(() -> search(group("AND", conds.toArray(String[]::new))));
    conds.add(cond("amountMinor", "GTE", "1"));
    rejectsFilter("filter.children[30]", group("AND", conds.toArray(String[]::new)));
  }

  @Test
  void inListsTextAndSortKeysAreBounded() {
    String hundred = String.join(",", java.util.Collections.nCopies(100, "\"CARD\""));
    assertDoesNotThrow(() -> search(cond("paymentMethod", "IN", "[" + hundred + "]")));
    rejectsFilter("filter.value", cond("paymentMethod", "IN", "[" + hundred + ",\"CARD\"]"));
    rejectsFilter("filter.value", cond("paymentMethod", "IN", "[]"));

    assertDoesNotThrow(() -> search(cond("title", "CONTAINS", "\"" + "x".repeat(280) + "\"")));
    rejectsFilter("filter.value", cond("title", "CONTAINS", "\"" + "x".repeat(281) + "\""));
    // 280 emoji = 280 characters (560 UTF-16 units): counted by code point
    assertDoesNotThrow(() -> search(cond("title", "CONTAINS", "\"" + "😀".repeat(280) + "\"")));

    String s = "{\"field\":\"%s\",\"direction\":\"ASC\"}";
    assertEquals(
        4,
        searchRaw(
                "{\"sort\":["
                    + String.format(s, "title")
                    + ","
                    + String.format(s, "note")
                    + ","
                    + String.format(s, "amountMinor")
                    + "]}")
            .query()
            .sort()
            .size(),
        "3 keys + id");
    rejects(
        "sort",
        "{\"sort\":["
            + String.format(s, "title")
            + ","
            + String.format(s, "note")
            + ","
            + String.format(s, "amountMinor")
            + ","
            + String.format(s, "type")
            + "]}");
    rejects(
        "sort[1].field",
        "{\"sort\":[" + String.format(s, "title") + "," + String.format(s, "title") + "]}");
  }

  @Test
  void sortDefaultsAndTieBreaker() {
    List<QuerySpec.SortKey> def = searchRaw("{}").query().sort();
    assertEquals(QueryRequestParser.DEFAULT_SORT, def);
    List<QuerySpec.SortKey> custom =
        searchRaw("{\"sort\":[{\"field\":\"amountMinor\",\"direction\":\"DESC\"}]}").query().sort();
    assertEquals(2, custom.size());
    assertEquals(TxField.ID, custom.get(1).field());
    assertTrue(custom.get(1).asc());
    List<QuerySpec.SortKey> explicit =
        searchRaw("{\"sort\":[{\"field\":\"id\",\"direction\":\"DESC\"}]}").query().sort();
    assertEquals(1, explicit.size(), "id already present, nothing appended");
    rejects("sort[0].direction", "{\"sort\":[{\"field\":\"title\"}]}");
    rejects("sort[0].direction", "{\"sort\":[{\"field\":\"title\",\"direction\":\"asc\"}]}");
    rejects("sort[0].field", "{\"sort\":[{\"field\":\"version\",\"direction\":\"ASC\"}]}");
  }

  @Test
  void pageDefaultsAndLimits() {
    assertEquals(new QuerySpec.Page(0, 50), searchRaw("{}").page());
    assertEquals(
        new QuerySpec.Page(100_000, 200),
        searchRaw("{\"page\":{\"offset\":100000,\"limit\":200}}").page());
    rejects("page.limit", "{\"page\":{\"limit\":0}}");
    rejects("page.limit", "{\"page\":{\"limit\":201}}");
    rejects("page.offset", "{\"page\":{\"offset\":-1}}");
    rejects("page.offset", "{\"page\":{\"offset\":1.5}}");
    ApiException e =
        assertThrows(ApiException.class, () -> searchRaw("{\"page\":{\"offset\":100001}}"));
    assertEquals("PAGE_OFFSET_LIMIT_EXCEEDED", e.code());
  }

  // ------------------------------------------------------------------ values

  @Test
  void valueTypesAreValidatedBeforeAnySql() {
    rejectsFilter("filter.value", cond("categoryId", "EQ", "\"not-a-uuid\""));
    rejectsFilter(
        "filter.value[0]", cond("categoryId", "IN", "[\"11111111-1111-1111-1111-11111111111\"]"));
    rejectsFilter("filter.value", cond("type", "EQ", "\"income\""));
    rejectsFilter("filter.value", cond("paymentMethod", "EQ", "\"CHEQUE\""));
    rejectsFilter("filter.value", cond("amountMinor", "EQ", "\"5\""));
    rejectsFilter("filter.value", cond("amountMinor", "EQ", "5.5"));
    rejectsFilter("filter.value", cond("amountMinor", "EQ", "5.0"));
    rejectsFilter("filter.value", cond("amountMinor", "EQ", "-1"));
    rejectsFilter("filter.value", cond("amountMinor", "EQ", "1000000000000000"));
    rejectsFilter("filter.value", cond("amountMinor", "EQ", "true"));
    rejectsFilter("filter.value", cond("occurredOn", "EQ", "\"2026-02-30\""));
    rejectsFilter("filter.value", cond("occurredOn", "EQ", "\"2026-2-3\""));
    rejectsFilter("filter.value", cond("occurredAt", "GT", "\"2026-01-31T10:00:00\""));
    rejectsFilter("filter.value", cond("title", "EQ", "5"));
    rejectsFilter("filter.value", cond("title", "EQ", "\"\""));
    rejectsFilter("filter.value", cond("title", "EQ", "\"a\\u0000b\""));
    rejectsFilter("filter.value", cond("title", "IS_NULL", "\"x\""));
    rejectsFilter("filter.value", cond("title", "EQ", null));
    rejectsFilter("filter.value", cond("title", "EQ", "null"));
  }

  @Test
  void rangesAreInclusiveButMustNotBeReversed() {
    assertDoesNotThrow(() -> search(cond("amountMinor", "BETWEEN", "[5,5]")));
    rejectsFilter("filter.value", cond("amountMinor", "BETWEEN", "[6,5]"));
    rejectsFilter("filter.value", cond("occurredOn", "BETWEEN", "[\"2026-02-01\",\"2026-01-01\"]"));
    rejectsFilter(
        "filter.value",
        cond("occurredAt", "BETWEEN", "[\"2026-01-01T00:00:01Z\",\"2026-01-01T00:00:00Z\"]"));
    rejectsFilter("filter.value", cond("amountMinor", "BETWEEN", "[1,2,3]"));
    rejectsFilter("filter.value", cond("amountMinor", "BETWEEN", "5"));
  }

  @Test
  void unknownKeysBadKindsAndMalformedJsonAreRejected() {
    rejects("bogus", "{\"bogus\":1}");
    rejects(
        "filter.extra",
        "{\"filter\":{\"kind\":\"group\",\"op\":\"AND\",\"children\":[],\"extra\":1}}");
    rejects("filter.kind", "{\"filter\":{\"kind\":\"nope\"}}");
    rejects("filter.op", "{\"filter\":{\"kind\":\"group\",\"op\":\"XOR\",\"children\":[]}}");
    rejects("filter", "{\"filter\":[]}");
    rejectsFilter("filter.field", cond("x", "EQ", "1"));
    assertEquals(
        "BAD_REQUEST", assertThrows(ApiException.class, () -> searchRaw("{\"filter\":")).code());
    rejects("$", "[]");
    // duplicate keys are ambiguous and refused
    assertEquals(
        "BAD_REQUEST",
        assertThrows(ApiException.class, () -> searchRaw("{\"page\":{},\"page\":{}}")).code());
  }

  // ------------------------------------------------------------------ analyze + fingerprint

  @Test
  void analyzeRequiresBucketAndWindow() {
    var ok =
        parser.parseAnalyze(
            "{\"bucket\":\"MONTH\",\"window\":{\"startDate\":\"2025-01-01\",\"endDate\":\"2025-12-31\"}}"
                .getBytes(StandardCharsets.UTF_8));
    assertEquals(QuerySpec.Bucket.MONTH, ok.bucket());
    assertEquals(LocalDate.of(2025, 12, 31), ok.window().endDate());
    for (String bad :
        new String[] {
          "{\"window\":{\"startDate\":\"2025-01-01\",\"endDate\":\"2025-12-31\"}}",
          "{\"bucket\":\"WEEK\",\"window\":{\"startDate\":\"2025-01-01\",\"endDate\":\"2025-12-31\"}}",
          "{\"bucket\":\"DAY\"}",
          "{\"bucket\":\"DAY\",\"window\":{\"startDate\":\"2025-01-01\"}}",
          "{\"bucket\":\"DAY\",\"window\":{\"startDate\":\"2025-02-01\",\"endDate\":\"2025-01-01\"}}",
          "{\"bucket\":\"DAY\",\"page\":{},\"window\":{\"startDate\":\"2025-01-01\",\"endDate\":\"2025-01-02\"}}"
        }) {
      assertThrows(
          ApiException.class, () -> parser.parseAnalyze(bad.getBytes(StandardCharsets.UTF_8)), bad);
    }
  }

  @Test
  void windowIsAppendedToAndGroupsAndWrapsEverythingElse() {
    QuerySpec.Window w = new QuerySpec.Window(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
    FilterNode and =
        QueryRequestParser.withWindow(
            search(group("AND", cond("title", "EQ", "\"a\""))).query().filter(), w);
    assertEquals(2, ((FilterNode.Group) and).children().size());
    FilterNode or =
        QueryRequestParser.withWindow(
            search(group("OR", cond("title", "EQ", "\"a\""))).query().filter(), w);
    assertEquals(FilterNode.GroupOp.AND, ((FilterNode.Group) or).op());
    assertEquals(2, FilterNode.depth(or));
    assertEquals(
        1,
        FilterNode.conditionCount(
            QueryRequestParser.withWindow(search(group("AND")).query().filter(), w)));
  }

  @Test
  void fingerprintIsStableAndNormalizesValues() {
    String a =
        QueryFingerprint.of(
            search(cond("occurredAt", "GT", "\"2026-01-01T01:00:00+01:00\"")).query().filter(),
            null);
    String b =
        QueryFingerprint.of(
            search(cond("occurredAt", "GT", "\"2026-01-01T00:00:00Z\"")).query().filter(), null);
    String c =
        QueryFingerprint.of(
            search(cond("occurredAt", "GT", "\"2026-01-01T00:00:01Z\"")).query().filter(), null);
    assertEquals(a, b, "same instant, different offset");
    assertNotEquals(a, c);
    assertEquals(64, a.length());
  }

  @Test
  void likeEscapingIsLiteral() {
    assertEquals("100\\%\\_\\\\x", TransactionPredicateCompiler.escapeLike("100%_\\x"));
  }
}
