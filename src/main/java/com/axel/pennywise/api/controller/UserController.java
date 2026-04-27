package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.user.MeResponse;
import com.axel.pennywise.domain.auth.AuthService;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class UserController {

    private static final String LOCAL = "local";

    private final UserService userService;
    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication auth) {
        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        return ResponseEntity.ok(authService.toMeResponse(user));
    }
}
