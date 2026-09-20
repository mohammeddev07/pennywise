package com.axel.pennywise.domain.transaction.query;

import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.transaction.TransactionEntity;
import com.axel.pennywise.domain.transaction.TransactionType;
import com.axel.pennywise.domain.transaction.query.QuerySpec.SortKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Criteria-based reads for the filter engine. Every method takes the trusted {@code bookId}
 * separately from the user-supplied {@link FilterNode}; see {@link TransactionPredicateCompiler}
 * for how the two are combined. Callers own the transaction (read-only, repeatable-read when
 * several queries must agree).
 */
@Repository
public class TransactionQueryRepository {

  /** One (day, category, type) group of matching rows. */
  public record AggregateRow(
      LocalDate occurredOn,
      UUID categoryId,
      String categoryName,
      TransactionType type,
      BigDecimal totalMinor,
      long count) {}

  @PersistenceContext private EntityManager em;

  private final TransactionPredicateCompiler compiler;

  public TransactionQueryRepository(TransactionPredicateCompiler compiler) {
    this.compiler = compiler;
  }

  public long count(UUID bookId, FilterNode filter) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Long> q = cb.createQuery(Long.class);
    Root<TransactionEntity> tx = q.from(TransactionEntity.class);
    Join<TransactionEntity, CategoryEntity> category = tx.join("category", JoinType.INNER);
    q.select(cb.count(tx)).where(compiler.compile(cb, tx, category, bookId, filter));
    return em.createQuery(q).getSingleResult();
  }

  /** One page, category fetched in the same statement (no N+1). */
  public List<TransactionEntity> page(
      UUID bookId, FilterNode filter, List<SortKey> sort, int offset, int limit) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<TransactionEntity> q = cb.createQuery(TransactionEntity.class);
    Root<TransactionEntity> tx = q.from(TransactionEntity.class);
    Join<TransactionEntity, CategoryEntity> category = fetchCategory(tx);
    q.select(tx)
        .where(compiler.compile(cb, tx, category, bookId, filter))
        .orderBy(compiler.orders(cb, tx, category, sort));
    return em.createQuery(q).setFirstResult(offset).setMaxResults(limit).getResultList();
  }

  public Optional<TransactionEntity> largestExpense(UUID bookId, FilterNode filter) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<TransactionEntity> q = cb.createQuery(TransactionEntity.class);
    Root<TransactionEntity> tx = q.from(TransactionEntity.class);
    Join<TransactionEntity, CategoryEntity> category = fetchCategory(tx);
    Predicate where =
        cb.and(
            compiler.compile(cb, tx, category, bookId, filter),
            cb.equal(tx.get("type"), TransactionType.EXPENSE));
    List<Order> order =
        List.of(
            cb.desc(tx.get("amountMinor")),
            cb.desc(tx.get("occurredOn")),
            cb.desc(tx.get("createdAt")),
            cb.desc(tx.get("id")));
    q.select(tx).where(where).orderBy(order);
    return em.createQuery(q).setMaxResults(1).getResultList().stream().findFirst();
  }

  /** All matching rows, grouped by (day, category, type) inside PostgreSQL. */
  public List<AggregateRow> aggregate(UUID bookId, FilterNode filter) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Tuple> q = cb.createTupleQuery();
    Root<TransactionEntity> tx = q.from(TransactionEntity.class);
    Join<TransactionEntity, CategoryEntity> category = tx.join("category", JoinType.INNER);
    q.multiselect(
            tx.get("occurredOn"),
            category.get("id"),
            category.get("name"),
            tx.get("type"),
            // numeric, not bigint: a legacy pile of huge rows must not overflow in the driver
            cb.toBigDecimal(cb.sum(tx.<Long>get("amountMinor"))),
            cb.count(tx))
        .where(compiler.compile(cb, tx, category, bookId, filter))
        .groupBy(tx.get("occurredOn"), category.get("id"), category.get("name"), tx.get("type"));
    List<AggregateRow> out = new ArrayList<>();
    for (Tuple t : em.createQuery(q).getResultList()) {
      out.add(
          new AggregateRow(
              t.get(0, LocalDate.class),
              t.get(1, UUID.class),
              t.get(2, String.class),
              t.get(3, TransactionType.class),
              t.get(4, BigDecimal.class),
              t.get(5, Long.class)));
    }
    return out;
  }

  /** Drops managed entities already consumed by a chunked reader so memory stays bounded. */
  public void clear() {
    em.clear();
  }

  @SuppressWarnings("unchecked")
  private static Join<TransactionEntity, CategoryEntity> fetchCategory(Root<TransactionEntity> tx) {
    Object fetch = tx.fetch("category", JoinType.INNER); // a Hibernate fetch is also a Join
    return (Join<TransactionEntity, CategoryEntity>) fetch;
  }
}
