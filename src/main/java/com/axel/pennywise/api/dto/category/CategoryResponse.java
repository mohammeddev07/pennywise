package com.axel.pennywise.api.dto.category;

import com.axel.pennywise.domain.category.CategoryType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID bookId,
        CategoryType type,
        String name,
        boolean isDisabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        long version
) {}
