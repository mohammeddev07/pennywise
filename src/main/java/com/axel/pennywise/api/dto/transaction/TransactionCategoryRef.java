package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.category.CategoryType;
import java.util.UUID;

public record TransactionCategoryRef(
        UUID id,
        String name,
        CategoryType type
) {}
