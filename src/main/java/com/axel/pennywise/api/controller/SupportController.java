package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.support.ContactRequest;
import com.axel.pennywise.domain.support.SupportMailSender;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import com.axel.pennywise.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/support")
@RequiredArgsConstructor
public class SupportController {
  private final UserService userService;
  private final SupportMailSender mailSender;

  @PostMapping("/contact")
  public ResponseEntity<Void> contact(
      Authentication authentication,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody ContactRequest request) {
    String subject = request.subject().trim();
    String message = request.message().trim();
    if (subject.isEmpty()
        || message.isEmpty()
        || subject.contains("\r")
        || subject.contains("\n")) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Subject and message are required.");
    }
    if (!idempotencyKey.matches("[A-Za-z0-9_-]{8,100}")) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid Idempotency-Key header.");
    }

    String subjectId =
        CurrentUser.subject()
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized"));
    UserEntity user =
        userService.getOrCreate(authentication, subjectId, CurrentUser.email().orElse(null));
    String email = user.getEmail();
    if (email == null || email.isBlank()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "EMAIL_REQUIRED",
          "Add an email address to your account before contacting support.");
    }
    mailSender.send(user.getId(), email, subject, message, idempotencyKey);
    return ResponseEntity.noContent().build();
  }
}
