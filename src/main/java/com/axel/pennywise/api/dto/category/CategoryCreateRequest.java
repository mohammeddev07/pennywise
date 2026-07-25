package com.axel.pennywise.api.dto.category;

import com.axel.pennywise.domain.category.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotNull CategoryType type,
        @NotBlank @Size(max = 60) String name,
        @Size(max = 64) String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color
) {
    public CategoryCreateRequest(CategoryType type, String name) {
        this(type, name, null, null);
    }
}
