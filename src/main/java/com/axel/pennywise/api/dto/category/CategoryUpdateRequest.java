package com.axel.pennywise.api.dto.category;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @Size(max = 60) String name,
        Boolean isDisabled,
        @Size(max = 64) String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color
) {
    public CategoryUpdateRequest(String name, Boolean isDisabled) {
        this(name, isDisabled, null, null);
    }
}
