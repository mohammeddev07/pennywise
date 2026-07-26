package com.axel.pennywise.api.dto.user;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeResponse(
    UUID id, String email, String defaultCurrencyCode, OffsetDateTime createdAt) {}
