package com.axel.pennywise.api.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Omitted or null fields stay unchanged; a blank displayName clears it. */
public record MeUpdateRequest(
    @Pattern(regexp = "^[A-Za-z]{3}$") String defaultCurrencyCode,
    @Size(max = 40) String displayName) {}
