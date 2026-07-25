package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.auth.AuthRequest;
import com.axel.pennywise.api.dto.auth.AuthResponse;
import com.axel.pennywise.api.dto.auth.LoginRequest;
import com.axel.pennywise.domain.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/signup")
  public ResponseEntity<AuthResponse> signup(@Valid @RequestBody AuthRequest req) {
    return ResponseEntity.status(201)
        .body(authService.signup(req.email(), req.password(), req.defaultCurrencyCode()));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req.email(), req.password()));
  }
}
