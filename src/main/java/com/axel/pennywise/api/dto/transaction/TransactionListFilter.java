package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.transaction.TransactionType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionListFilter(
        UUID bookId,
        LocalDate fromDate,
        LocalDate toDate,
        TransactionType type,
        UUID categoryId,
        String q,
        int limit,
        String cursor,

        // decoded cursor components (nullable)
        LocalDate cursorOccurredOn,
        OffsetDateTime cursorCreatedAt,
        UUID cursorId
) {
    public TransactionListFilter withDecodedCursor(LocalDate occurredOn, OffsetDateTime createdAt, UUID id) {
        return new TransactionListFilter(
                bookId, fromDate, toDate, type, categoryId, q, limit, cursor,
                occurredOn, createdAt, id
        );
    }
}
