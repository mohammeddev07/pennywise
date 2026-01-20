package com.axel.pennywise.api.dto.category;

import com.axel.pennywise.domain.category.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        CategoryType type,
        @NotBlank @Size(max = 60) String name
) {}
