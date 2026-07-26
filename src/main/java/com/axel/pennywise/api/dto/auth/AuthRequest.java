package com.axel.pennywise.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthRequest(
    @Email @NotBlank @Size(max = 320) String email,
    @NotBlank @Size(min = 8, max = 128) String password,
    @Pattern(regexp = "^[A-Za-z]{3}$") String defaultCurrencyCode) {}
