package com.axel.pennywise.api.controller;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HealthController {
  @GetMapping("/health")
  public Map<String, String> health() {
    log.info("HEALTH check");
    return Map.of("status", "UP");
  }
}
