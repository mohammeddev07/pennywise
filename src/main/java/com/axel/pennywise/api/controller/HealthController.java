package com.axel.pennywise.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
public class HealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        log.info("HEALTH check");
        return Map.of("status", "UP");
    }
}
