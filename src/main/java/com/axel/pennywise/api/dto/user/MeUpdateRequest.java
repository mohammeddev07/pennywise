package com.axel.pennywise.api.dto.user;

import jakarta.validation.constraints.Pattern;

public record MeUpdateRequest(@Pattern(regexp = "^[A-Za-z]{3}$") String defaultCurrencyCode) {}
