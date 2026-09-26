package com.axel.pennywise.domain.transaction.query.ai;

import com.axel.pennywise.domain.transaction.query.FilterNode;
import com.axel.pennywise.domain.transaction.query.FilterNode.Condition;
import com.axel.pennywise.domain.transaction.query.FilterNode.Group;
import com.axel.pennywise.domain.transaction.query.FilterNode.GroupOp;
import com.axel.pennywise.domain.transaction.query.TxField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Renders a validated {@link FilterNode} as plain English. Built from the AST the same way the P1
 * response already echoes {@code effectiveFilter} as canonical JSON - this just formats that
 * structure for a person instead of a machine. Never touches model prose.
 */
@Component
class FilterSummaryFormatter {

  String describe(FilterNode node, Map<UUID, String> categoryNames, int minorUnitDigits, String currencyCode) {
    String text = render(node, categoryNames, minorUnitDigits, currencyCode);
    return text.isBlank() ? "all transactions" : text;
  }

  private String render(
      FilterNode node, Map<UUID, String> categoryNames, int digits, String currency) {
    if (node instanceof Group g) {
      if (g.children().isEmpty()) return "";
      String joiner = g.op() == GroupOp.AND ? " and " : " or ";
      List<String> parts =
          g.children().stream().map(c -> render(c, categoryNames, digits, currency)).toList();
      if (parts.size() == 1) return parts.get(0);
      return String.join(joiner, parts.stream().map(this::maybeParen).toList());
    }
    return describeCondition((Condition) node, categoryNames, digits, currency);
  }

  private String maybeParen(String part) {
    boolean mixed = part.contains(" and ") || part.contains(" or ");
    return mixed ? "(" + part + ")" : part;
  }

  private String describeCondition(
      Condition c, Map<UUID, String> categoryNames, int digits, String currency) {
    String label = fieldLabel(c.field());
    String phrase =
        switch (c.operator()) {
          case EQ -> "is " + value(c.field(), c.value(), categoryNames, digits, currency);
          case NE -> "is not " + value(c.field(), c.value(), categoryNames, digits, currency);
          case IN -> "is one of " + listValue(c.field(), c.value(), categoryNames);
          case NOT_IN -> "is none of " + listValue(c.field(), c.value(), categoryNames);
          case GT -> "is greater than " + value(c.field(), c.value(), categoryNames, digits, currency);
          case GTE -> "is at least " + value(c.field(), c.value(), categoryNames, digits, currency);
          case LT -> "is less than " + value(c.field(), c.value(), categoryNames, digits, currency);
          case LTE -> "is at most " + value(c.field(), c.value(), categoryNames, digits, currency);
          case BETWEEN -> {
            @SuppressWarnings("unchecked")
            List<Object> range = (List<Object>) c.value();
            yield "is between "
                + scalar(c.field(), range.get(0), categoryNames, digits, currency)
                + " and "
                + scalar(c.field(), range.get(1), categoryNames, digits, currency);
          }
          case CONTAINS -> "contains \"" + c.value() + "\"";
          case NOT_CONTAINS -> "does not contain \"" + c.value() + "\"";
          case STARTS_WITH -> "starts with \"" + c.value() + "\"";
          case ENDS_WITH -> "ends with \"" + c.value() + "\"";
          case IS_NULL -> "is empty";
          case IS_NOT_NULL -> "is set";
        };
    return label + " " + phrase;
  }

  private String fieldLabel(TxField field) {
    return switch (field) {
      case TYPE -> "type";
      case AMOUNT_MINOR -> "amount";
      case OCCURRED_ON -> "date";
      case CATEGORY_ID -> "category";
      case CATEGORY_NAME -> "category name";
      case PAYMENT_METHOD -> "payment method";
      case TITLE -> "title";
      case NOTE -> "note";
      case DESCRIPTION -> "title or note";
      default -> field.wireName();
    };
  }

  private String value(
      TxField field, Object v, Map<UUID, String> categoryNames, int digits, String currency) {
    return scalar(field, v, categoryNames, digits, currency);
  }

  @SuppressWarnings("unchecked")
  private String listValue(TxField field, Object v, Map<UUID, String> categoryNames) {
    List<Object> list = (List<Object>) v;
    return list.stream()
        .map(x -> scalar(field, x, categoryNames, 2, ""))
        .reduce((a, b) -> a + ", " + b)
        .orElse("");
  }

  private String scalar(
      TxField field, Object v, Map<UUID, String> categoryNames, int digits, String currency) {
    if (field == TxField.CATEGORY_ID && v instanceof UUID id) {
      return categoryNames.getOrDefault(id, "an unknown category");
    }
    if (field == TxField.AMOUNT_MINOR && v instanceof Long minor) {
      String amount = BigDecimal.valueOf(minor).movePointLeft(digits).toPlainString();
      return currency.isBlank() ? amount : amount + " " + currency;
    }
    if (v instanceof LocalDate d) return d.toString();
    if (v instanceof OffsetDateTime t) return t.toString();
    if (v instanceof Enum<?> e) return e.name();
    return String.valueOf(v);
  }
}
