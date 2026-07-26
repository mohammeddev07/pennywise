package com.axel.pennywise.config;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TestSecureController {
  @GetMapping("/secure")
  String secure() {
    return "ok";
  }

  @PreAuthorize("hasAuthority('SCOPE_admin')")
  @GetMapping("/secure/admin")
  String admin() {
    return "ok";
  }
}
