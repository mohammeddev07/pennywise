package com.axel.pennywise.api.dto.category;

import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @Size(max = 60) String name,
        Boolean isDisabled
) {}
