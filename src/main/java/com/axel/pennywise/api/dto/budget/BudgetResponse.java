package com.axel.pennywise.api.dto.budget;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BudgetResponse(
    UUID id,
    UUID bookId,
    UUID categoryId,
    String categoryName,
    String month,
    long amountMinor,
    long spentMinor,
    long remainingMinor,
    String currencyCode,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    long version) {}
