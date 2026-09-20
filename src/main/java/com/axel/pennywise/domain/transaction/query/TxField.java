package com.axel.pennywise.domain.transaction.query;

import com.axel.pennywise.domain.transaction.PaymentMethod;
import com.axel.pennywise.domain.transaction.TransactionType;
import java.util.EnumSet;
import java.util.Set;

/**
 * The single source of truth for what a client may filter and sort on. The capability matrix
 * published in the docs and OpenAPI is derived from this enum by hand; {@code
 * TransactionQueryParserTest} fails if the two drift.
 *
 * <p>Deliberately absent: {@code bookId} (the endpoint path is the scope), {@code deletedAt}
 * (always {@code null} - soft-deleted rows are never visible) and {@code version} (an
 * optimistic-locking token, not user data).
 */
public enum TxField {
  ID("id", Kind.UUID, false),
  TYPE("type", Kind.ENUM, false),
  AMOUNT_MINOR("amountMinor", Kind.NUMBER, false),
  OCCURRED_ON("occurredOn", Kind.DATE, false),
  OCCURRED_AT("occurredAt", Kind.TIMESTAMP, true),
  CATEGORY_ID("categoryId", Kind.UUID, false),
  CATEGORY_NAME("categoryName", Kind.TEXT, false),
  PAYMENT_METHOD("paymentMethod", Kind.ENUM, true),
  TITLE("title", Kind.TEXT, true),
  NOTE("note", Kind.TEXT, true),
  /** Virtual: matches {@code title} OR {@code note}. */
  DESCRIPTION("description", Kind.TEXT, false),
  CREATED_AT("createdAt", Kind.TIMESTAMP, false),
  UPDATED_AT("updatedAt", Kind.TIMESTAMP, false),
  EXTERNAL_ID("externalId", Kind.TEXT, true);

  public enum Kind {
    UUID,
    ENUM,
    NUMBER,
    DATE,
    TIMESTAMP,
    TEXT
  }

  private final String wireName;
  private final Kind kind;
  private final boolean nullable;

  TxField(String wireName, Kind kind, boolean nullable) {
    this.wireName = wireName;
    this.kind = kind;
    this.nullable = nullable;
  }

  public String wireName() {
    return wireName;
  }

  public Kind kind() {
    return kind;
  }

  public boolean nullable() {
    return nullable;
  }

  /** Enum class behind an ENUM field (values are matched by exact upper-case name). */
  public Class<? extends Enum<?>> enumClass() {
    return switch (this) {
      case TYPE -> TransactionType.class;
      case PAYMENT_METHOD -> PaymentMethod.class;
      default -> throw new IllegalStateException(this + " is not an enum field");
    };
  }

  public Set<FilterOperator> operators() {
    Set<FilterOperator> ops =
        switch (kind) {
          case UUID, ENUM ->
              EnumSet.of(
                  FilterOperator.EQ, FilterOperator.NE, FilterOperator.IN, FilterOperator.NOT_IN);
          case NUMBER, DATE, TIMESTAMP ->
              EnumSet.of(
                  FilterOperator.EQ,
                  FilterOperator.NE,
                  FilterOperator.GT,
                  FilterOperator.GTE,
                  FilterOperator.LT,
                  FilterOperator.LTE,
                  FilterOperator.BETWEEN);
          case TEXT ->
              EnumSet.of(
                  FilterOperator.EQ,
                  FilterOperator.NE,
                  FilterOperator.CONTAINS,
                  FilterOperator.NOT_CONTAINS,
                  FilterOperator.STARTS_WITH,
                  FilterOperator.ENDS_WITH);
        };
    if (nullable) {
      ops.add(FilterOperator.IS_NULL);
      ops.add(FilterOperator.IS_NOT_NULL);
    }
    return ops;
  }

  public static TxField fromWire(String name) {
    for (TxField f : values()) {
      if (f.wireName.equals(name)) return f;
    }
    return null;
  }
}
