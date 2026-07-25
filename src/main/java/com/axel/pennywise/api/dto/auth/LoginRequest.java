package com.axel.pennywise.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @Email @NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 128) String password) {}
