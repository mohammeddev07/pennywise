package com.axel.pennywise.domain.transaction.query.ai;

import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.common.MoneyLimits;
import com.axel.pennywise.domain.transaction.query.FilterNode;
import com.axel.pennywise.domain.transaction.query.FilterNode.Condition;
import com.axel.pennywise.domain.transaction.query.FilterNode.Group;
import com.axel.pennywise.domain.transaction.query.FilterOperator;
import com.axel.pennywise.domain.transaction.query.QueryRequestParser;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Query;
import com.axel.pennywise.domain.transaction.query.TxField;
import com.axel.pennywise.domain.transaction.query.ai.ProviderProposal.ProviderCondition;
import com.axel.pennywise.domain.transaction.query.ai.ProviderProposal.ProviderGroup;
import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Turns a {@link ProviderProposal} (already schema-shaped by Gemini) into a validated {@link Query}
 * by building the exact wire JSON {@link QueryRequestParser} already accepts from the manual
 * advanced builder, then running it through that same parser. This is deliberately the only place
 * that understands the provider's shape; everything downstream of {@link #buildValidatedQuery} is
 * indistinguishable from a hand-built filter.
 */
@Slf4j
@Component
class FilterProposalMapper {

  private final QueryRequestParser parser;
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  FilterProposalMapper(QueryRequestParser parser) {
    this.parser = parser;
  }

  Query buildValidatedQuery(
      ProviderProposal proposal,
      BookEntity book,
      Set<UUID> ownedCategoryIds,
      LocalDate referenceDate) {
    List<ProviderGroup> groups = proposal.groups();
    if (groups == null || groups.isEmpty()) {
      throw invalid("proposal had no filter groups");
    }

    List<ObjectNode> groupNodes =
        groups.stream().map(g -> mapGroup(g, book, referenceDate)).toList();

    ObjectNode filterNode;
    if (groupNodes.size() == 1) {
      filterNode = groupNodes.get(0);
    } else {
      String topOp = "OR".equals(proposal.topOp()) ? "OR" : "AND";
      ArrayNode children = mapper.createArrayNode();
      groupNodes.forEach(children::add);
      filterNode = mapper.createObjectNode().put("kind", "group").put("op", topOp);
      filterNode.set("children", children);
    }

    ObjectNode root = mapper.createObjectNode();
    root.set("filter", filterNode);
    ArrayNode sort = mapSort(proposal);
    if (sort != null) root.set("sort", sort);

    Query query;
    try {
      query = parser.parseQueryBody(mapper.writeValueAsBytes(root));
    } catch (ApiException e) {
      log.debug("AI proposal failed P1 validation: {}", e.getMessage());
      throw invalid("proposal did not pass filter validation");
    } catch (Exception e) {
      throw invalid("proposal could not be serialized");
    }

    requireOwnedCategoriesAndAllowedFields(query.filter(), ownedCategoryIds);
    return query;
  }

  private ObjectNode mapGroup(ProviderGroup group, BookEntity book, LocalDate referenceDate) {
    if (group == null || group.conditions() == null || group.conditions().isEmpty()) {
      throw invalid("a group had no conditions");
    }
    String op = "OR".equals(group.op()) ? "OR" : "AND";
    ArrayNode children = mapper.createArrayNode();
    for (ProviderCondition c : group.conditions()) {
      children.add(mapCondition(c, book, referenceDate));
    }
    ObjectNode node = mapper.createObjectNode().put("kind", "group").put("op", op);
    node.set("children", children);
    return node;
  }

  private ObjectNode mapCondition(ProviderCondition c, BookEntity book, LocalDate referenceDate) {
    if (c == null || c.field() == null) throw invalid("condition missing field");
    TxField field = TxField.fromWire(c.field());
    if (field == null || !AiFilterFields.ALLOWED.contains(field)) {
      throw invalid("condition used an unsupported field: " + c.field());
    }
    FilterOperator operator;
    try {
      operator = c.operator() == null ? null : FilterOperator.valueOf(c.operator());
    } catch (IllegalArgumentException e) {
      operator = null;
    }
    if (operator == null) throw invalid("condition used an unsupported operator: " + c.operator());
    // A date preset always resolves to a closed [start, end] range. Forcing BETWEEN here means
    // "today"/"this month" behave the same regardless of which scalar operator (EQ, GTE, ...) the
    // model happened to pick for it, and the array value below is never paired with a scalar
    // operator that QueryRequestParser would reject.
    if (field.kind() == TxField.Kind.DATE && c.datePreset() != null) {
      operator = FilterOperator.BETWEEN;
    }

    ObjectNode node =
        mapper
            .createObjectNode()
            .put("kind", "condition")
            .put("field", field.wireName())
            .put("operator", operator.name());

    if (operator == FilterOperator.IS_NULL || operator == FilterOperator.IS_NOT_NULL) {
      return node; // no value
    }

    switch (field.kind()) {
      case NUMBER -> node.set("value", numberValue(c, operator, book));
      case DATE -> node.set("value", dateValue(c, operator, referenceDate));
      case ENUM -> node.set("value", enumValue(c, operator));
      case UUID -> node.set("value", listOrScalarText(c, operator, "category"));
      case TEXT -> node.put("value", requireText(c.stringValue(), "text"));
      case TIMESTAMP -> throw invalid("timestamp fields are not offered to the AI proposer");
    }
    return node;
  }

  private com.fasterxml.jackson.databind.JsonNode numberValue(
      ProviderCondition c, FilterOperator op, BookEntity book) {
    int digits = MoneyLimits.minorUnitDigits(book.getCurrencyCode());
    if (op == FilterOperator.BETWEEN) {
      if (c.numberArrayValue() == null || c.numberArrayValue().size() != 2) {
        throw invalid("amount range needs exactly two values");
      }
      ArrayNode arr = mapper.createArrayNode();
      c.numberArrayValue().forEach(v -> arr.add(toMinorUnits(v, digits)));
      return arr;
    }
    if (c.numberValue() == null) throw invalid("amount condition is missing a value");
    return mapper.getNodeFactory().numberNode(toMinorUnits(c.numberValue(), digits));
  }

  private static long toMinorUnits(double majorUnits, int digits) {
    return BigDecimal.valueOf(majorUnits)
        .movePointRight(digits)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact();
  }

  private com.fasterxml.jackson.databind.JsonNode dateValue(
      ProviderCondition c, FilterOperator op, LocalDate referenceDate) {
    if (c.datePreset() != null) {
      DatePreset preset;
      try {
        preset = DatePreset.valueOf(c.datePreset());
      } catch (IllegalArgumentException e) {
        throw invalid("unknown date preset: " + c.datePreset());
      }
      DatePreset.Range range = preset.resolve(referenceDate);
      ArrayNode arr = mapper.createArrayNode();
      arr.add(range.start().toString());
      arr.add(range.end().toString());
      return arr;
    }
    if (op == FilterOperator.BETWEEN) {
      if (c.stringArrayValue() == null || c.stringArrayValue().size() != 2) {
        throw invalid("date range needs exactly two values");
      }
      ArrayNode arr = mapper.createArrayNode();
      c.stringArrayValue().forEach(arr::add);
      return arr;
    }
    return mapper.getNodeFactory().textNode(requireText(c.dateValue(), "date"));
  }

  private com.fasterxml.jackson.databind.JsonNode enumValue(
      ProviderCondition c, FilterOperator op) {
    if (op == FilterOperator.IN || op == FilterOperator.NOT_IN) {
      if (c.stringArrayValue() == null || c.stringArrayValue().isEmpty()) {
        throw invalid("enum list condition is missing values");
      }
      ArrayNode arr = mapper.createArrayNode();
      c.stringArrayValue().forEach(v -> arr.add(v.toUpperCase(Locale.ROOT)));
      return arr;
    }
    return mapper
        .getNodeFactory()
        .textNode(requireText(c.stringValue(), "enum").toUpperCase(Locale.ROOT));
  }

  private com.fasterxml.jackson.databind.JsonNode listOrScalarText(
      ProviderCondition c, FilterOperator op, String label) {
    if (op == FilterOperator.IN || op == FilterOperator.NOT_IN) {
      if (c.stringArrayValue() == null || c.stringArrayValue().isEmpty()) {
        throw invalid(label + " list condition is missing values");
      }
      ArrayNode arr = mapper.createArrayNode();
      c.stringArrayValue().forEach(arr::add);
      return arr;
    }
    return mapper.getNodeFactory().textNode(requireText(c.stringValue(), label));
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) throw invalid(label + " condition is missing a value");
    return value;
  }

  private ArrayNode mapSort(ProviderProposal proposal) {
    if (proposal.sortField() == null) return null;
    TxField field = TxField.fromWire(proposal.sortField());
    if (field == null || !AiFilterFields.ALLOWED.contains(field)) return null;
    String direction = "DESC".equals(proposal.sortDirection()) ? "DESC" : "ASC";
    ArrayNode sort = mapper.createArrayNode();
    ObjectNode key = mapper.createObjectNode();
    key.put("field", field.wireName());
    key.put("direction", direction);
    sort.add(key);
    return sort;
  }

  /**
   * Belt-and-braces after the P1 parser already validated shape/limits: a category id must actually
   * belong to this book, and every field must still be one the AI was allowed to use (the schema
   * already guarantees the latter; this is defense in depth, not the primary control).
   */
  private void requireOwnedCategoriesAndAllowedFields(FilterNode node, Set<UUID> ownedCategoryIds) {
    if (node instanceof Group g) {
      g.children().forEach(c -> requireOwnedCategoriesAndAllowedFields(c, ownedCategoryIds));
      return;
    }
    Condition c = (Condition) node;
    if (!AiFilterFields.ALLOWED.contains(c.field())) {
      throw invalid("condition used a field outside the AI capability set: " + c.field());
    }
    if (c.field() != TxField.CATEGORY_ID || c.value() == null) return;
    List<?> ids = c.value() instanceof List<?> list ? list : List.of(c.value());
    for (Object id : ids) {
      if (id instanceof UUID uuid && !ownedCategoryIds.contains(uuid)) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "AI_FILTER_INVALID_CATEGORY",
            "That filter referenced a category that doesn't belong to this book.");
      }
    }
  }

  private static ApiException invalid(String detail) {
    log.debug("AI filter proposal rejected: {}", detail);
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "AI_FILTER_INVALID",
        "Couldn't build a valid filter from that question. Try rephrasing, or use the manual"
            + " filter.");
  }
}
