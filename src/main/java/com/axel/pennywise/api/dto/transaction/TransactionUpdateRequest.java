package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.transaction.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record TransactionUpdateRequest(
        TransactionType type,
        @Min(1) Long amountMinor,
        LocalDate occurredOn,
        UUID categoryId,
        @Size(max = 280) String note
) {}
