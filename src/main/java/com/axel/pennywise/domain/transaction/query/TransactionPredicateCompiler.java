package com.axel.pennywise.domain.transaction.query;

import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.transaction.TransactionEntity;
import com.axel.pennywise.domain.transaction.query.FilterNode.Condition;
import com.axel.pennywise.domain.transaction.query.FilterNode.Group;
import com.axel.pennywise.domain.transaction.query.FilterNode.GroupOp;
import com.axel.pennywise.domain.transaction.query.QuerySpec.SortKey;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.stereotype.Component;

/**
 * Compiles a validated {@link FilterNode} into JPA Criteria predicates and orders. Shared by
 * search, analyze and query export so all three see exactly the same rows.
 *
 * <p>Safety properties:
 *
 * <ul>
 *   <li>The result is always {@code ownedBook AND activeTransaction AND categoryInBook AND
 *       (userExpression)}; the user expression is one parenthesised operand, so no OR group can
 *       widen the tenant scope.
 *   <li>Field names come from the {@link TxField} enum, never from request text, and every value is
 *       a bound parameter (Criteria literals use Hibernate's BIND value-handling mode).
 *   <li>The category join is created once by the caller and passed in, so there is never a second
 *       join and never an N+1. Categories are not filtered by {@code deletedAt}/{@code isDisabled}:
 *       an active transaction keeps its historical category.
 * </ul>
 */
@Component
public class TransactionPredicateCompiler {

  private static final char ESC = '\\';

  public Predicate compile(
      CriteriaBuilder cb,
      Root<TransactionEntity> tx,
      Join<TransactionEntity, CategoryEntity> category,
      UUID bookId,
      FilterNode filter) {
    Predicate trusted =
        cb.and(
            cb.equal(tx.get("book").get("id"), bookId),
            cb.isNull(tx.get("deletedAt")),
            cb.equal(category.get("book").get("id"), bookId));
    if (filter instanceof Group g && g.children().isEmpty()) return trusted;
    return cb.and(trusted, node(cb, tx, category, filter));
  }

  public List<Order> orders(
      CriteriaBuilder cb,
      Root<TransactionEntity> tx,
      Join<TransactionEntity, CategoryEntity> category,
      List<SortKey> sort) {
    HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
    List<Order> out = new ArrayList<>();
    for (SortKey k : sort) {
      for (Expression<?> e : sortExpressions(cb, tx, category, k.field())) {
        if (k.field().nullable()) {
          // nullsFirst=false -> explicit NULLS LAST in both directions
          out.add(k.asc() ? hcb.asc(e, false) : hcb.desc(e, false));
        } else {
          // Nothing to place: the column is NOT NULL (description is coalesced). Omitting the
          // clause keeps the sort order equal to the default index order, so the default
          // "occurredOn DESC, createdAt DESC, id DESC" list uses ix_tx_book_occurred_id_active
          // (0.2 ms) instead of sorting the whole book (45 ms on 200k rows). See docs.
          out.add(k.asc() ? cb.asc(e) : cb.desc(e));
        }
      }
    }
    return out;
  }

  // ---------------------------------------------------------------- predicates

  private Predicate node(
      CriteriaBuilder cb,
      Root<TransactionEntity> tx,
      Join<TransactionEntity, CategoryEntity> category,
      FilterNode n) {
    if (n instanceof Group g) {
      Predicate[] kids =
          g.children().stream().map(c -> node(cb, tx, category, c)).toArray(Predicate[]::new);
      return g.op() == GroupOp.AND ? cb.and(kids) : cb.or(kids);
    }
    return condition(cb, tx, category, (Condition) n);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Predicate condition(
      CriteriaBuilder cb,
      Root<TransactionEntity> tx,
      Join<TransactionEntity, CategoryEntity> category,
      Condition c) {
    if (c.field() == TxField.DESCRIPTION) return description(cb, tx, c);

    Expression<?> col = column(tx, category, c.field());
    Object v = c.value();
    // Non-null NE/NOT_CONTAINS rely on SQL three-valued logic: NULL <> x is not true, so rows
    // with a null value are excluded, as documented.
    return switch (c.operator()) {
      case IS_NULL -> cb.isNull(col);
      case IS_NOT_NULL -> cb.isNotNull(col);
      case IN -> col.in((List<?>) v);
      case NOT_IN -> cb.not(col.in((List<?>) v));
      case BETWEEN -> {
        List<?> range = (List<?>) v;
        yield cb.between(
            (Expression<Comparable>) col, (Comparable) range.get(0), (Comparable) range.get(1));
      }
      case GT -> cb.greaterThan((Expression<Comparable>) col, (Comparable) v);
      case GTE -> cb.greaterThanOrEqualTo((Expression<Comparable>) col, (Comparable) v);
      case LT -> cb.lessThan((Expression<Comparable>) col, (Comparable) v);
      case LTE -> cb.lessThanOrEqualTo((Expression<Comparable>) col, (Comparable) v);
      case EQ ->
          c.field().kind() == TxField.Kind.TEXT
              ? text(cb, (Expression<String>) col, c)
              : cb.equal(col, v);
      case NE ->
          c.field().kind() == TxField.Kind.TEXT
              ? cb.not(text(cb, (Expression<String>) col, c))
              : cb.notEqual(col, v);
      case CONTAINS, STARTS_WITH, ENDS_WITH -> text(cb, (Expression<String>) col, c);
      case NOT_CONTAINS -> cb.not(text(cb, (Expression<String>) col, c));
    };
  }

  /**
   * Virtual field: title OR note, both coalesced to '' so the negative operators are the plain
   * logical negation (a row with neither title nor note is NOT_CONTAINS anything).
   */
  private Predicate description(CriteriaBuilder cb, Root<TransactionEntity> tx, Condition c) {
    Predicate any =
        cb.or(
            text(cb, cb.coalesce(tx.<String>get("title"), value(cb, "")), c),
            text(cb, cb.coalesce(tx.<String>get("note"), value(cb, "")), c));
    return switch (c.operator()) {
      case NE, NOT_CONTAINS -> cb.not(any);
      default -> any;
    };
  }

  /** Positive form of a text operator: literal, case-insensitive; NE/NOT_CONTAINS negate it. */
  private Predicate text(CriteriaBuilder cb, Expression<String> col, Condition c) {
    String v = (String) c.value();
    Expression<String> lhs = cb.lower(col);
    return switch (c.operator()) {
      case EQ, NE -> cb.equal(lhs, lowerValue(cb, v));
      case CONTAINS, NOT_CONTAINS -> like(cb, lhs, "%" + escapeLike(v) + "%");
      case STARTS_WITH -> like(cb, lhs, escapeLike(v) + "%");
      case ENDS_WITH -> like(cb, lhs, "%" + escapeLike(v));
      default -> throw new IllegalStateException("not a text operator: " + c.operator());
    };
  }

  private Predicate like(CriteriaBuilder cb, Expression<String> lhs, String pattern) {
    return cb.like(lhs, lowerValue(cb, pattern), ESC);
  }

  /**
   * A bound parameter, not {@code cb.literal(...)}: Hibernate renders explicit criteria literals
   * inline as SQL text, whereas {@code value(...)} goes through the JDBC parameter binding.
   */
  private static Expression<String> value(CriteriaBuilder cb, String v) {
    return ((HibernateCriteriaBuilder) cb).value(v);
  }

  private static Expression<String> lowerValue(CriteriaBuilder cb, String v) {
    return cb.lower(value(cb, v));
  }

  static String escapeLike(String s) {
    StringBuilder b = new StringBuilder(s.length() + 4);
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (ch == ESC || ch == '%' || ch == '_') b.append(ESC);
      b.append(ch);
    }
    return b.toString();
  }

  // ---------------------------------------------------------------- fields

  private Expression<?> column(
      Root<TransactionEntity> tx, Join<TransactionEntity, CategoryEntity> category, TxField f) {
    return switch (f) {
      case ID -> tx.get("id");
      case TYPE -> tx.get("type");
      case AMOUNT_MINOR -> tx.get("amountMinor");
      case OCCURRED_ON -> tx.get("occurredOn");
      case OCCURRED_AT -> tx.get("occurredAt");
      case CATEGORY_ID -> tx.get("category").get("id"); // FK column, no join needed
      case CATEGORY_NAME -> category.get("name");
      case PAYMENT_METHOD -> tx.get("paymentMethod");
      case TITLE -> tx.get("title");
      case NOTE -> tx.get("note");
      case CREATED_AT -> tx.get("createdAt");
      case UPDATED_AT -> tx.get("updatedAt");
      case EXTERNAL_ID -> tx.get("externalId");
      case DESCRIPTION -> throw new IllegalStateException("virtual field has no column");
    };
  }

  /**
   * Text sorts on lower(col) (case-normalized); description sorts by coalesced title, then note.
   */
  @SuppressWarnings("unchecked")
  private List<Expression<?>> sortExpressions(
      CriteriaBuilder cb,
      Root<TransactionEntity> tx,
      Join<TransactionEntity, CategoryEntity> category,
      TxField f) {
    if (f == TxField.DESCRIPTION) {
      return List.of(
          cb.lower(cb.coalesce(tx.<String>get("title"), value(cb, ""))),
          cb.lower(cb.coalesce(tx.<String>get("note"), value(cb, ""))));
    }
    Expression<?> col = column(tx, category, f);
    return List.of(f.kind() == TxField.Kind.TEXT ? cb.lower((Expression<String>) col) : col);
  }
}
