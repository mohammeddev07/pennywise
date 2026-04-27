package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.transaction.TransactionType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID bookId,
        TransactionType type,
        long amountMinor,
        LocalDate occurredOn,
        UUID categoryId,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        long version
) {}
