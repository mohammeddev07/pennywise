package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.transaction.PaymentMethod;
import com.axel.pennywise.domain.transaction.TransactionType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code occurredOn} is the canonical ledger date (book-local); {@code occurredAt} is the event
 * instant. {@code createdAt} is the record's creation time and never changes; {@code updatedAt}
 * and {@code version} advance only when a write actually changes the row. {@code externalId} is
 * the read-only import identifier.
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
    long version) {}
