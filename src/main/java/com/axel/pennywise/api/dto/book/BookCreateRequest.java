package com.axel.pennywise.api.dto.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookCreateRequest(
    @NotBlank @Size(max = 80) String name,
    @NotBlank @Size(min = 3, max = 3) String currencyCode,
    @NotBlank String timezone,
    long openingBalanceMinor) {}
