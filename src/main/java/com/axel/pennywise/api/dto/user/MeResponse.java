package com.axel.pennywise.api.dto.user;

import com.axel.pennywise.domain.user.UserEntity;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MeResponse(
    UUID id,
    String email,
    String displayName,
    String defaultCurrencyCode,
    OffsetDateTime createdAt) {

  public static MeResponse from(UserEntity user) {
    return new MeResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getDefaultCurrencyCode(),
        user.getCreatedAt());
  }
}
