package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.transaction.PaymentMethod;
import com.axel.pennywise.domain.transaction.TransactionEntity;
import com.axel.pennywise.domain.transaction.TransactionType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code occurredOn} is the canonical ledger date (book-local); {@code occurredAt} is the event
 * instant. {@code createdAt} is the record's creation time and never changes; {@code updatedAt} and
 * {@code version} advance only when a write actually changes the row. {@code externalId} is the
 * read-only import identifier.
 */
public record TransactionResponse(
    UUID id,
    UUID bookId,
    TransactionType type,
    long amountMinor,
    LocalDate occurredOn,
    OffsetDateTime occurredAt,
    String title,
    UUID categoryId,
    TransactionCategoryRef category,
    PaymentMethod paymentMethod,
    String note,
    String externalId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt,
    long version) {

  /**
   * Maps a row whose category is already loaded. {@code bookId} is passed in because the book
   * association is a lazy proxy that is never fetched by the query endpoints.
   */
  public static TransactionResponse from(TransactionEntity tx, UUID bookId) {
    return new TransactionResponse(
        tx.getId(),
        bookId,
        tx.getType(),
        tx.getAmountMinor(),
        tx.getOccurredOn(),
        tx.getOccurredAt(),
        tx.getTitle(),
        tx.getCategory().getId(),
        new TransactionCategoryRef(
            tx.getCategory().getId(), tx.getCategory().getName(), tx.getCategory().getType()),
        tx.getPaymentMethod(),
        tx.getNote(),
        tx.getExternalId(),
        tx.getCreatedAt(),
        tx.getUpdatedAt(),
        tx.getDeletedAt(),
        tx.getVersion() == null ? 0 : tx.getVersion());
  }
}
