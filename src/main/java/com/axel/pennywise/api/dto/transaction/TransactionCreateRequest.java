package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.transaction.PaymentMethod;
import com.axel.pennywise.domain.transaction.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionCreateRequest(
        @NotNull TransactionType type,
        @Min(1) long amountMinor,
        LocalDate occurredOn,
        @NotNull UUID categoryId,
        @Size(max = 280) String note,
        @Size(max = 120) String title,
        PaymentMethod paymentMethod,
        OffsetDateTime occurredAt
) {
    public TransactionCreateRequest(TransactionType type, long amountMinor, LocalDate occurredOn, UUID categoryId, String note) {
        this(type, amountMinor, occurredOn, categoryId, note, null, null, null);
    }

    public LocalDate resolvedOccurredOn() {
        if (occurredOn != null) {
            return occurredOn;
        }
        return occurredAt == null ? null : occurredAt.toLocalDate();
    }
}
