package com.axel.pennywise.domain.transaction.query;

import com.axel.pennywise.domain.common.MoneyLimits;
import com.axel.pennywise.domain.transaction.query.FilterNode.Condition;
import com.axel.pennywise.domain.transaction.query.FilterNode.Group;
import com.axel.pennywise.domain.transaction.query.FilterNode.GroupOp;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Analyze;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Bucket;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Page;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Query;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Search;
import com.axel.pennywise.domain.transaction.query.QuerySpec.SortKey;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Window;
import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Turns a raw request body into a validated {@link Query}/{@link Page}/{@link Analyze}. Everything
 * a client can get wrong (shape, unknown keys, field/operator pairs, value types, limits) is
 * rejected here with a JSON path, before any SQL is built. The tree is walked by hand instead of
 * bound through Jackson polymorphism so error paths are precise and nesting is capped before
 * recursing.
 */
@Component
public class QueryRequestParser {

  public static final int MAX_BODY_BYTES = 64 * 1024;
  public static final int MAX_DEPTH = 3;
  public static final int MAX_CONDITIONS = 30;
  public static final int MAX_IN_VALUES = 100;
  public static final int MAX_SORT_KEYS = 3;
  public static final int MAX_TEXT_CHARS = 280;
  public static final int DEFAULT_LIMIT = 50;
  public static final int MAX_LIMIT = 200;
  public static final int MAX_OFFSET = 100_000;

  public static final List<SortKey> DEFAULT_SORT =
      List.of(
          new SortKey(TxField.OCCURRED_ON, false),
          new SortKey(TxField.CREATED_AT, false),
          new SortKey(TxField.ID, false));

  private static final Pattern UUID_RE =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private static final Pattern DATE_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

  // Duplicate keys would let a client smuggle two meanings into one object; refuse them.
  private final ObjectMapper mapper =
      JsonMapper.builder().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build();

  public Search parseSearch(byte[] body) {
    JsonNode root = readRoot(body, Set.of("filter", "sort", "page"));
    return new Search(parseQuery(root), parsePage(root.get("page")));
  }

  public Query parseQueryBody(byte[] body) {
    return parseQuery(readRoot(body, Set.of("filter", "sort")));
  }

  public Analyze parseAnalyze(byte[] body) {
    JsonNode root = readRoot(body, Set.of("filter", "bucket", "window"));
    FilterNode filter = parseFilter(root.get("filter"));

    JsonNode b = root.get("bucket");
    if (b == null || !b.isTextual()) fail("bucket", "is required: DAY, MONTH or YEAR");
    Bucket bucket;
    try {
      bucket = Bucket.valueOf(b.asText());
    } catch (IllegalArgumentException e) {
      throw failure("bucket", "must be DAY, MONTH or YEAR");
    }

    JsonNode w = root.get("window");
    if (w == null || !w.isObject()) fail("window", "is required: {startDate, endDate}");
    checkKeys(w, "window", Set.of("startDate", "endDate"));
    LocalDate start = date(w.get("startDate"), "window.startDate");
    LocalDate end = date(w.get("endDate"), "window.endDate");
    if (start.isAfter(end)) fail("window", "startDate must be on or before endDate");
    return new Analyze(filter, bucket, new Window(start, end));
  }

  /**
   * {@code filter AND occurredOn BETWEEN window}: the predicate analyze runs, and search should
   * send.
   */
  public static FilterNode withWindow(FilterNode filter, Window window) {
    Condition inWindow =
        new Condition(
            TxField.OCCURRED_ON,
            FilterOperator.BETWEEN,
            List.of(window.startDate(), window.endDate()));
    if (filter instanceof Group g && g.op() == GroupOp.AND) {
      List<FilterNode> kids = new ArrayList<>(g.children());
      kids.add(inWindow);
      return new Group(GroupOp.AND, kids);
    }
    return new Group(GroupOp.AND, List.of(filter, inWindow));
  }

  /** Limits for a filter that was assembled server-side (analysis window appended). */
  public static void requireWithinLimits(FilterNode effective) {
    if (FilterNode.depth(effective) > MAX_DEPTH
        || FilterNode.conditionCount(effective) > MAX_CONDITIONS) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "filter plus the window condition exceeds "
              + MAX_CONDITIONS
              + " conditions or "
              + MAX_DEPTH
              + " levels of nesting");
    }
  }

  // ---------------------------------------------------------------- query / sort / page

  private Query parseQuery(JsonNode root) {
    return new Query(parseFilter(root.get("filter")), parseSort(root.get("sort")));
  }

  private FilterNode parseFilter(JsonNode node) {
    if (node == null || node.isNull()) return new Group(GroupOp.AND, List.of());
    return parseNode(node, "filter", 0, true, new int[] {0});
  }

  private FilterNode parseNode(JsonNode n, String path, int parentDepth, boolean top, int[] count) {
    if (!n.isObject()) fail(path, "must be an object");
    JsonNode kind = n.get("kind");
    if (kind == null || !kind.isTextual())
      fail(path + ".kind", "is required: 'group' or 'condition'");
    return switch (kind.asText()) {
      case "group" -> parseGroup(n, path, parentDepth + 1, top, count);
      case "condition" -> parseCondition(n, path, count);
      default -> throw failure(path + ".kind", "must be 'group' or 'condition'");
    };
  }

  private FilterNode parseGroup(JsonNode n, String path, int depth, boolean top, int[] count) {
    checkKeys(n, path, Set.of("kind", "op", "children"));
    if (depth > MAX_DEPTH) fail(path, "groups can nest at most " + MAX_DEPTH + " levels deep");
    JsonNode opNode = n.get("op");
    if (opNode == null || !opNode.isTextual()) fail(path + ".op", "is required: AND or OR");
    GroupOp op;
    try {
      op = GroupOp.valueOf(opNode.asText());
    } catch (IllegalArgumentException e) {
      throw failure(path + ".op", "must be AND or OR");
    }
    JsonNode kids = n.get("children");
    if (kids == null || !kids.isArray())
      fail(path + ".children", "is required and must be an array");
    if (kids.isEmpty()) {
      if (top && op == GroupOp.AND) return new Group(op, List.of()); // "everything"
      throw failure(
          path + ".children",
          "empty group; only an empty top-level AND is allowed (meaning: all rows)");
    }
    List<FilterNode> out = new ArrayList<>();
    for (int i = 0; i < kids.size(); i++) {
      out.add(parseNode(kids.get(i), path + ".children[" + i + "]", depth, false, count));
    }
    return new Group(op, out);
  }

  private FilterNode parseCondition(JsonNode n, String path, int[] count) {
    checkKeys(n, path, Set.of("kind", "field", "operator", "value"));
    if (++count[0] > MAX_CONDITIONS)
      fail(path, "at most " + MAX_CONDITIONS + " conditions are allowed");

    JsonNode f = n.get("field");
    TxField field = (f != null && f.isTextual()) ? TxField.fromWire(f.asText()) : null;
    if (field == null) {
      fail(
          path + ".field",
          "unknown field; allowed: "
              + Arrays.stream(TxField.values()).map(TxField::wireName).toList());
    }
    JsonNode o = n.get("operator");
    FilterOperator op = null;
    if (o != null && o.isTextual()) {
      try {
        op = FilterOperator.valueOf(o.asText());
      } catch (IllegalArgumentException ignored) {
        // falls through to the failure below
      }
    }
    if (op == null) fail(path + ".operator", "is required and must be a known operator");
    if (!field.operators().contains(op)) {
      fail(
          path + ".operator",
          op + " is not supported on " + field.wireName() + "; allowed: " + field.operators());
    }

    JsonNode v = n.get("value");
    boolean hasValue = v != null && !v.isNull();
    String vp = path + ".value";
    if (op == FilterOperator.IS_NULL || op == FilterOperator.IS_NOT_NULL) {
      if (hasValue) fail(vp, "must be omitted for " + op);
      return new Condition(field, op, null);
    }
    if (!hasValue) fail(vp, "is required for " + op);

    if (op == FilterOperator.IN || op == FilterOperator.NOT_IN) {
      if (!v.isArray() || v.isEmpty()) fail(vp, "must be a non-empty array");
      if (v.size() > MAX_IN_VALUES) fail(vp, "at most " + MAX_IN_VALUES + " values are allowed");
      List<Object> values = new ArrayList<>();
      for (int i = 0; i < v.size(); i++) values.add(scalar(field, v.get(i), vp + "[" + i + "]"));
      return new Condition(field, op, List.copyOf(values));
    }
    if (op == FilterOperator.BETWEEN) {
      if (!v.isArray() || v.size() != 2) fail(vp, "must be a two-element array [lower, upper]");
      Object lo = scalar(field, v.get(0), vp + "[0]");
      Object hi = scalar(field, v.get(1), vp + "[1]");
      @SuppressWarnings({"unchecked", "rawtypes"})
      int cmp = ((Comparable) lo).compareTo(hi);
      if (cmp > 0) fail(vp, "reversed range: lower bound is greater than upper bound");
      return new Condition(field, op, List.of(lo, hi));
    }
    return new Condition(field, op, scalar(field, v, vp));
  }

  private Object scalar(TxField field, JsonNode v, String path) {
    return switch (field.kind()) {
      case UUID -> uuid(v, path);
      case ENUM -> enumValue(field, v, path);
      case NUMBER -> amount(v, path);
      case DATE -> date(v, path);
      case TIMESTAMP -> timestamp(v, path);
      case TEXT -> text(v, path);
    };
  }

  private static UUID uuid(JsonNode v, String path) {
    if (!v.isTextual() || !UUID_RE.matcher(v.asText()).matches())
      fail(path, "must be a UUID string");
    return UUID.fromString(v.asText());
  }

  private static Object enumValue(TxField field, JsonNode v, String path) {
    Enum<?>[] constants = field.enumClass().getEnumConstants();
    if (v.isTextual()) {
      for (Enum<?> c : constants) if (c.name().equals(v.asText())) return c;
    }
    throw failure(path, "must be one of " + Arrays.stream(constants).map(Enum::name).toList());
  }

  private static Long amount(JsonNode v, String path) {
    if (!v.isIntegralNumber() || !v.canConvertToLong())
      fail(path, "must be an integer (minor units)");
    long l = v.longValue();
    if (l < 0 || l > MoneyLimits.MAX_TRANSACTION_AMOUNT_MINOR) {
      fail(path, "must be between 0 and " + MoneyLimits.MAX_TRANSACTION_AMOUNT_MINOR);
    }
    return l;
  }

  private static LocalDate date(JsonNode v, String path) {
    if (v == null || !v.isTextual() || !DATE_RE.matcher(v.asText()).matches()) {
      throw failure(path, "must be a YYYY-MM-DD date");
    }
    try {
      return LocalDate.parse(v.asText());
    } catch (DateTimeParseException e) {
      throw failure(path, "is not a valid calendar date");
    }
  }

  private static OffsetDateTime timestamp(JsonNode v, String path) {
    if (!v.isTextual())
      fail(path, "must be an ISO-8601 timestamp with offset, e.g. 2026-01-31T10:15:00Z");
    try {
      // PostgreSQL stores microseconds; truncating keeps EQ/BETWEEN aligned with what is stored.
      return OffsetDateTime.parse(v.asText())
          .withOffsetSameInstant(ZoneOffset.UTC)
          .truncatedTo(ChronoUnit.MICROS);
    } catch (DateTimeParseException e) {
      throw failure(path, "must be an ISO-8601 timestamp with offset, e.g. 2026-01-31T10:15:00Z");
    }
  }

  private static String text(JsonNode v, String path) {
    if (!v.isTextual()) fail(path, "must be a string");
    String s = v.asText();
    if (s.isEmpty()) fail(path, "must not be empty (use IS_NULL to look for missing values)");
    if (s.codePointCount(0, s.length()) > MAX_TEXT_CHARS)
      fail(path, "at most " + MAX_TEXT_CHARS + " characters are allowed");
    if (s.indexOf('\u0000') >= 0) fail(path, "must not contain NUL characters");
    return s;
  }

  private List<SortKey> parseSort(JsonNode node) {
    if (node == null || node.isNull() || (node.isArray() && node.isEmpty())) return DEFAULT_SORT;
    if (!node.isArray()) fail("sort", "must be an array");
    if (node.size() > MAX_SORT_KEYS)
      fail("sort", "at most " + MAX_SORT_KEYS + " sort keys are allowed");
    List<SortKey> keys = new ArrayList<>();
    for (int i = 0; i < node.size(); i++) {
      String p = "sort[" + i + "]";
      JsonNode k = node.get(i);
      if (!k.isObject()) fail(p, "must be an object {field, direction}");
      checkKeys(k, p, Set.of("field", "direction"));
      JsonNode f = k.get("field");
      TxField field = (f != null && f.isTextual()) ? TxField.fromWire(f.asText()) : null;
      if (field == null) fail(p + ".field", "unknown field");
      JsonNode d = k.get("direction");
      if (d == null || !d.isTextual() || !Set.of("ASC", "DESC").contains(d.asText())) {
        fail(p + ".direction", "is required: ASC or DESC");
      }
      if (keys.stream().anyMatch(x -> x.field() == field))
        fail(p + ".field", "duplicate sort field");
      keys.add(new SortKey(field, d.asText().equals("ASC")));
    }
    if (keys.stream().noneMatch(x -> x.field() == TxField.ID))
      keys.add(new SortKey(TxField.ID, true));
    return List.copyOf(keys);
  }

  private Page parsePage(JsonNode node) {
    if (node == null || node.isNull()) return new Page(0, DEFAULT_LIMIT);
    if (!node.isObject()) fail("page", "must be an object {offset, limit}");
    checkKeys(node, "page", Set.of("offset", "limit"));
    int offset = intField(node, "offset", 0);
    int limit = intField(node, "limit", DEFAULT_LIMIT);
    if (offset < 0) fail("page.offset", "must be >= 0");
    if (offset > MAX_OFFSET) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "PAGE_OFFSET_LIMIT_EXCEEDED",
          "page.offset must be <= "
              + MAX_OFFSET
              + ". Narrow the filter or change the sort instead of paging deeper.",
          List.of(Map.of("path", "page.offset", "message", "max " + MAX_OFFSET)));
    }
    if (limit < 1 || limit > MAX_LIMIT) fail("page.limit", "must be between 1 and " + MAX_LIMIT);
    return new Page(offset, limit);
  }

  private static int intField(JsonNode node, String name, int dflt) {
    JsonNode v = node.get(name);
    if (v == null || v.isNull()) return dflt;
    if (!v.isIntegralNumber() || !v.canConvertToInt()) fail("page." + name, "must be an integer");
    return v.intValue();
  }

  // ---------------------------------------------------------------- plumbing

  private JsonNode readRoot(byte[] body, Set<String> allowed) {
    JsonNode root;
    try {
      root = mapper.readTree(body);
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed JSON");
    }
    if (root == null || !root.isObject()) fail("$", "request body must be a JSON object");
    checkKeys(root, "$", allowed);
    return root;
  }

  private static void checkKeys(JsonNode node, String path, Set<String> allowed) {
    Iterator<String> it = node.fieldNames();
    while (it.hasNext()) {
      String key = it.next();
      if (!allowed.contains(key))
        fail(path.equals("$") ? key : path + "." + key, "unknown property");
    }
  }

  private static void fail(String path, String message) {
    throw failure(path, message);
  }

  private static ApiException failure(String path, String message) {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        path + ": " + message,
        List.of(Map.of("path", path, "message", message)));
  }
}
