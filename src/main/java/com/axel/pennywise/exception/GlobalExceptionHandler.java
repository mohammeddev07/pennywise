package com.axel.pennywise.exception;

import com.axel.pennywise.util.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        log.warn("API error: code={}, message={}, status={}", ex.code(), ex.getMessage(), ex.status());
        return ResponseEntity.status(ex.status()).body(error(ex.code(), ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, Object>> details = ex.getBindingResult().getAllErrors().stream()
                .map(err -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    if (err instanceof FieldError fe) {
                        m.put("field", fe.getField());
                    }
                    m.put("message", err.getDefaultMessage());
                    return m;
                })
                .toList();

        log.warn("Validation error: error_count={}, errors={}", details.size(), details);
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", "Invalid request", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
        List<Map<String, Object>> details = ex.getConstraintViolations().stream()
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("path", v.getPropertyPath().toString());
                    m.put("message", v.getMessage());
                    return m;
                })
                .toList();

        log.warn("Constraint violation: violation_count={}, violations={}", details.size(), details);
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", "Invalid request", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBadJson(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: message={}", ex.getMessage());
        return ResponseEntity.badRequest().body(error("BAD_REQUEST", "Malformed JSON", List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("FORBIDDEN", "Authenticated user is not allowed to access this resource", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error in request: path={}, method={}", request.getRequestURI(), request.getMethod(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", "Unexpected error", List.of()));
    }

    private ErrorResponse error(String code, String message, List<? extends Map<String, ?>> details) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return new ErrorResponse(new ErrorResponse.Error(code, message, details == null ? List.of() : details, requestId));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        String header = ex.getHeaderName();

        // Special-case If-Match to keep your existing error semantics
        String code = "MISSING_HEADER";
        String message = "Missing required header: " + header;

        if ("If-Match".equalsIgnoreCase(header)) {
            code = "MISSING_IF_MATCH";
            message = "If-Match header is required";
        } else if ("Idempotency-Key".equalsIgnoreCase(header)) {
            code = "MISSING_IDEMPOTENCY_KEY";
            message = "Idempotency-Key header is required";
        }

        return ResponseEntity
                .badRequest()
                .body(error(code, message, List.of(Map.of("header", header))));
    }

}
