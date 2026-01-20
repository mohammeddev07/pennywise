package com.axel.pennywise.domain.transaction;

import com.axel.pennywise.domain.summary.CategoryTotal;
import com.axel.pennywise.domain.summary.SummaryTotalsView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {
    Optional<TransactionEntity> findByIdAndBook_IdAndDeletedAtIsNull(UUID id, UUID bookId);

    @Query("""
                select t
                from TransactionEntity t
                where t.deletedAt is null
                  and t.book.id = :bookId
                  and (:from is null or t.occurredOn >= :from)
                  and (:to is null or t.occurredOn <= :to)
                  and (:type is null or t.type = :type)
                  and (:categoryId is null or t.category.id = :categoryId)
            """)
    Page<TransactionEntity> listForBook(
            UUID bookId,
            LocalDate from,
            LocalDate to,
            TransactionType type,
            UUID categoryId,
            Pageable pageable
    );

    // Page 1 (no cursor)
    @Query("""
            select t
            from TransactionEntity t
              join fetch t.book b
              join fetch t.category c
            where b.id = :bookId
              and t.deletedAt is null
              and (:fromDate is null or t.occurredOn >= :fromDate)
              and (:toDate   is null or t.occurredOn <= :toDate)
              and (:type     is null or t.type = :type)
              and (:categoryId is null or c.id = :categoryId)
              and (:q is null or (t.note is not null and lower(t.note) like lower(concat('%', :q, '%'))))
            order by t.occurredOn desc, t.createdAt desc, t.id desc
            """)
    List<TransactionEntity> listForBookFirstPage(
            @Param("bookId") UUID bookId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("type") TransactionType type,
            @Param("categoryId") UUID categoryId,
            @Param("q") String q,
            Pageable pageable
    );

    // Next pages (cursor present)
    @SuppressWarnings("java:S107") // many params justified by query filtering (repository interface)
    @Query("""
            select t
            from TransactionEntity t
              join fetch t.book b
              join fetch t.category c
            where b.id = :bookId
              and t.deletedAt is null
              and (:fromDate is null or t.occurredOn >= :fromDate)
              and (:toDate   is null or t.occurredOn <= :toDate)
              and (:type     is null or t.type = :type)
              and (:categoryId is null or c.id = :categoryId)
              and (:q is null or (t.note is not null and lower(t.note) like lower(concat('%', :q, '%'))))
              and (
                   t.occurredOn < :cursorOccurredOn
                or (t.occurredOn = :cursorOccurredOn and t.createdAt < :cursorCreatedAt)
                or (t.occurredOn = :cursorOccurredOn and t.createdAt = :cursorCreatedAt and t.id < :cursorId)
              )
            order by t.occurredOn desc, t.createdAt desc, t.id desc
            """)
    List<TransactionEntity> listForBookAfterCursor(
            @Param("bookId") UUID bookId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("type") TransactionType type,
            @Param("categoryId") UUID categoryId,
            @Param("q") String q,
            @Param("cursorOccurredOn") LocalDate cursorOccurredOn,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );


    @Query("""
    select
      coalesce(sum(case when t.type = TransactionType.INCOME then t.amountMinor else 0 end), 0) as incomeTotalMinor,
      coalesce(sum(case when t.type = TransactionType.EXPENSE then t.amountMinor else 0 end), 0) as expenseTotalMinor
    from TransactionEntity t
    where t.deletedAt is null
      and t.book.id = :bookId
      and (:fromDate is null or t.occurredOn >= :fromDate)
      and (:toDate   is null or t.occurredOn <  :toDate)
    """)
    SummaryTotalsView sumTotals(@Param("bookId") UUID bookId,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate);

    @Query("""
            select new com.axel.pennywise.domain.summary.CategoryTotal(
              t.category.id,
              coalesce(sum(t.amountMinor), 0)
            )
            from TransactionEntity t
            where t.deletedAt is null
              and t.book.id = :bookId
              and t.type = :type
              and t.occurredOn >= :fromDate
              and t.occurredOn <  :toDate
            group by t.category.id
            order by coalesce(sum(t.amountMinor), 0) desc
            """)
    List<CategoryTotal> sumByCategory(
            @Param("bookId") UUID bookId,
            @Param("type") TransactionType type,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

}
