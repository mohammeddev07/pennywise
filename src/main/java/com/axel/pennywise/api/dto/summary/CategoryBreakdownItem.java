package com.axel.pennywise.api.dto.summary;

import com.axel.pennywise.domain.category.CategoryType;
import java.util.UUID;

public record CategoryBreakdownItem(
        UUID categoryId,
        String categoryName,
        CategoryType type,
        long totalMinor,
        Long budgetMinor
) {
    public CategoryBreakdownItem(UUID categoryId, long totalMinor) {
        this(categoryId, null, null, totalMinor, null);
    }
}
