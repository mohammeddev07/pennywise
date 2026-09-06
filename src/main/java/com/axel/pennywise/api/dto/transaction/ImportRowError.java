package com.axel.pennywise.api.dto.transaction;

public record ImportRowError(int rowNumber, String code, String message) {}
