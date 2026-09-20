package com.axel.pennywise.domain.transaction.query;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validated filter AST. Instances only come out of {@link QueryRequestParser} (or the analysis
 * window helper), so values are already typed: UUID, TransactionType/PaymentMethod, Long,
 * LocalDate, OffsetDateTime (UTC, microseconds) or String; IN/NOT_IN carry a {@code List}, BETWEEN
 * a two-element {@code List} (inclusive).
 */
public sealed interface FilterNode {

  enum GroupOp {
    AND,
    OR
  }

  record Group(GroupOp op, List<FilterNode> children) implements FilterNode {}

  record Condition(TxField field, FilterOperator operator, Object value) implements FilterNode {}

  /** Canonical JSON-shaped form: stable key order, normalized values. Used for the fingerprint. */
  default Map<String, Object> canonical() {
    Map<String, Object> m = new LinkedHashMap<>();
    if (this instanceof Group g) {
      m.put("kind", "group");
      m.put("op", g.op().name());
      List<Object> kids = new ArrayList<>();
      for (FilterNode c : g.children()) kids.add(c.canonical());
      m.put("children", kids);
    } else if (this instanceof Condition c) {
      m.put("kind", "condition");
      m.put("field", c.field().wireName());
      m.put("operator", c.operator().name());
      if (c.value() != null) m.put("value", canonicalValue(c.value()));
    }
    return m;
  }

  private static Object canonicalValue(Object v) {
    if (v instanceof List<?> list) return list.stream().map(FilterNode::canonicalValue).toList();
    if (v instanceof Enum<?> e) return e.name();
    if (v instanceof OffsetDateTime t) return DateTimeFormatter.ISO_INSTANT.format(t);
    if (v instanceof Long || v instanceof String) return v;
    return v.toString(); // UUID, LocalDate
  }

  static int depth(FilterNode n) {
    if (n instanceof Group g)
      return 1 + g.children().stream().mapToInt(FilterNode::depth).max().orElse(0);
    return 0;
  }

  static int conditionCount(FilterNode n) {
    if (n instanceof Group g)
      return g.children().stream().mapToInt(FilterNode::conditionCount).sum();
    return 1;
  }
}
