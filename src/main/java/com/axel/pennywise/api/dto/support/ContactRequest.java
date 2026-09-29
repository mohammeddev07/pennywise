package com.axel.pennywise.api.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
    @NotBlank @Size(max = 120) String subject, @NotBlank @Size(max = 5000) String message) {}
