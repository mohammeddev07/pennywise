package com.axel.pennywise.exception;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final HttpStatus status;
  private final String code;

  // Make serializable: List + Map + String are serializable
  private final List<Map<String, String>> details;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, List.of());
  }

  public ApiException(
      HttpStatus status, String code, String message, List<Map<String, String>> details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public List<Map<String, String>> details() {
    return details;
  }

  public record Detail(String key, String value) implements Serializable {}
}
