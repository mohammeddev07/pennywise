package com.axel.pennywise.domain.book;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<BookEntity, UUID> {
  @org.springframework.data.jpa.repository.Query(
      "select b from BookEntity b where b.owner.id = :ownerUserId and b.deletedAt is null order by"
          + " b.sortOrder, b.createdAt, b.id")
  List<BookEntity> findAllByOwner_IdAndDeletedAtIsNull(
      @org.springframework.data.repository.query.Param("ownerUserId") UUID ownerUserId);

  Optional<BookEntity> findByIdAndOwner_IdAndDeletedAtIsNull(UUID id, UUID ownerUserId);

  interface Totals {
    UUID getBookId();

    java.math.BigDecimal getIncome();

    java.math.BigDecimal getExpense();
  }

  // Return numeric totals, then apply the existing Java long balance arithmetic.
  @org.springframework.data.jpa.repository.Query(
      value =
          """
          SELECT b.id AS bookId,
            coalesce(sum(CASE WHEN t.type='INCOME' THEN t.amount_minor ELSE 0 END),0) AS income,
            coalesce(sum(CASE WHEN t.type='EXPENSE' THEN t.amount_minor ELSE 0 END),0) AS expense
          FROM expense_tracker.books b
          LEFT JOIN expense_tracker.transactions t ON t.book_id=b.id AND t.deleted_at IS NULL
          WHERE b.owner_user_id=:ownerId AND b.deleted_at IS NULL
          GROUP BY b.id
          """,
      nativeQuery = true)
  List<Totals> totalsForOwner(
      @org.springframework.data.repository.query.Param("ownerId") UUID ownerId);
}
