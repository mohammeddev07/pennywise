package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.auth.AuthRequest;
import com.axel.pennywise.api.dto.auth.AuthResponse;
import com.axel.pennywise.api.dto.auth.GoogleAuthRequest;
import com.axel.pennywise.api.dto.auth.LoginRequest;
import com.axel.pennywise.api.dto.user.MeResponse;
import com.axel.pennywise.domain.auth.AuthService;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final UserService userService;

  @PostMapping("/signup")
  public ResponseEntity<AuthResponse> signup(@Valid @RequestBody AuthRequest req) {
    return ResponseEntity.status(201)
        .body(authService.signup(req.email(), req.password(), req.defaultCurrencyCode()));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req.email(), req.password()));
  }

  @PostMapping("/google")
  public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleAuthRequest req) {
    return ResponseEntity.ok(authService.googleLogin(req.idToken()));
  }

  /** Requires a bearer token (see SecurityConfig): links Google to the logged-in account. */
  @PostMapping("/google/link")
  public ResponseEntity<MeResponse> linkGoogle(
      Authentication auth, @Valid @RequestBody GoogleAuthRequest req) {
    var user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElseThrow(), CurrentUser.email().orElse(null));
    return ResponseEntity.ok(authService.linkGoogle(user, req.idToken()));
  }
}
