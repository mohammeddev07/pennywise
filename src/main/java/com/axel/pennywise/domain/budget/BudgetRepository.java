package com.axel.pennywise.domain.budget;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<BudgetEntity, UUID> {

  @Query(
      """
      select b
      from BudgetEntity b
        join fetch b.book book
        join fetch b.category category
      where book.id = :bookId
        and b.monthStart = :monthStart
        and b.deletedAt is null
      order by category.name asc
      """)
  List<BudgetEntity> findAllActiveForBookAndMonth(
      @Param("bookId") UUID bookId, @Param("monthStart") LocalDate monthStart);

  @Query(
      """
      select b
      from BudgetEntity b
        join fetch b.book book
        join fetch b.category category
      where book.id = :bookId
        and category.id = :categoryId
        and b.monthStart = :monthStart
        and b.deletedAt is null
      """)
  Optional<BudgetEntity> findActive(
      @Param("bookId") UUID bookId,
      @Param("categoryId") UUID categoryId,
      @Param("monthStart") LocalDate monthStart);
}
