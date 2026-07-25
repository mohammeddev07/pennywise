package com.axel.pennywise.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void typeMismatchReturnsStandardBadRequestResponse() {
    MethodArgumentTypeMismatchException exception =
        new MethodArgumentTypeMismatchException(
            "not-a-uuid",
            java.util.UUID.class,
            "categoryId",
            org.mockito.Mockito.mock(MethodParameter.class),
            new IllegalArgumentException("invalid UUID"));

    var response = handler.handleTypeMismatch(exception);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("VALIDATION_ERROR", response.getBody().error().code());
    assertEquals(
        "Invalid value for request parameter: categoryId", response.getBody().error().message());
  }

  @Test
  void missingRequestParameterReturnsStandardBadRequestResponse() {
    MissingServletRequestParameterException exception =
        new MissingServletRequestParameterException("month", "String");

    var response = handler.handleMissingParameter(exception);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("VALIDATION_ERROR", response.getBody().error().code());
    assertEquals("Missing required request parameter: month", response.getBody().error().message());
  }

  @Test
  void invalidDateReturnsStandardBadRequestResponse() {
    DateTimeParseException exception = new DateTimeParseException("invalid date", "2026-99-99", 0);

    var response = handler.handleDateTimeParse(exception);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("VALIDATION_ERROR", response.getBody().error().code());
    assertEquals("Invalid date format. Use YYYY-MM-DD", response.getBody().error().message());
  }
}
