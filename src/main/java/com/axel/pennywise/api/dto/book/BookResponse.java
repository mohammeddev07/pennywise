package com.axel.pennywise.api.dto.book;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BookResponse(
    UUID id,
    String name,
    String currencyCode,
    String timezone,
    long openingBalanceMinor,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt,
    long version) {}
