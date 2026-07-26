package com.axel.pennywise.api.dto.auth;

import com.axel.pennywise.api.dto.user.MeResponse;

public record AuthResponse(
    String accessToken, String tokenType, long expiresInSeconds, MeResponse user) {}
