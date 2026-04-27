package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.transaction.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record TransactionCreateRequest(
        @NotNull TransactionType type,
        @Min(1) long amountMinor,
        @NotNull LocalDate occurredOn,
        @NotNull UUID categoryId,
        @Size(max = 280) String note
) {}
