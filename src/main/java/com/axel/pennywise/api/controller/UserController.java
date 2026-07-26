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
    return ResponseEntity.ok(toMeResponse(user));
  }

  @PatchMapping("/me")
  public ResponseEntity<MeResponse> patchMe(
      Authentication auth, @Valid @RequestBody MeUpdateRequest req) {
    if (req.defaultCurrencyCode() == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "PATCH request must contain at least one field");
    }

    UserEntity user = currentUser(auth);
    UserEntity updated = userService.updateDefaultCurrency(user, req.defaultCurrencyCode());
    return ResponseEntity.ok(toMeResponse(updated));
  }

  private UserEntity currentUser(Authentication auth) {
    return userService.getOrCreate(
        auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
  }

  private MeResponse toMeResponse(UserEntity user) {
    return new MeResponse(
        user.getId(), user.getEmail(), user.getDefaultCurrencyCode(), user.getCreatedAt());
  }
}
