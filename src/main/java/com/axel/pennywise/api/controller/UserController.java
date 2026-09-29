package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.user.MeResponse;
import com.axel.pennywise.api.dto.user.MeUpdateRequest;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import com.axel.pennywise.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class UserController {

  private static final String LOCAL = "local";

  private final UserService userService;

  @GetMapping("/me")
  public ResponseEntity<MeResponse> me(Authentication auth) {
    UserEntity user = currentUser(auth);
    return ResponseEntity.ok(MeResponse.from(user));
  }

  @PatchMapping("/me")
  public ResponseEntity<MeResponse> patchMe(
      Authentication auth, @Valid @RequestBody MeUpdateRequest req) {
    if (req.defaultCurrencyCode() == null && req.displayName() == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "PATCH request must contain at least one field");
    }

    UserEntity user = currentUser(auth);
    if (req.defaultCurrencyCode() != null) {
      user = userService.updateDefaultCurrency(user, req.defaultCurrencyCode());
    }
    if (req.displayName() != null) {
      user = userService.updateDisplayName(user, req.displayName());
    }
    return ResponseEntity.ok(MeResponse.from(user));
  }

  private UserEntity currentUser(Authentication auth) {
    return userService.getOrCreate(
        auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
  }
}
