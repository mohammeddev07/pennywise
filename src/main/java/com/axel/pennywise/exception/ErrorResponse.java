package com.axel.pennywise.exception;

import java.util.List;
import java.util.Map;

public record ErrorResponse(Error error) {
  public record Error(
      String code, String message, List<? extends Map<String, ?>> details, String requestId) {}
}
