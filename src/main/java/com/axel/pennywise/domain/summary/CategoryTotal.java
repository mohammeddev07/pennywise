package com.axel.pennywise.domain.summary;

import com.axel.pennywise.domain.category.CategoryType;
import java.util.UUID;

public record CategoryTotal(UUID categoryId, String categoryName, CategoryType type, long totalMinor) {
    public CategoryTotal(UUID categoryId, long totalMinor) {
        this(categoryId, null, null, totalMinor);
    }
}
