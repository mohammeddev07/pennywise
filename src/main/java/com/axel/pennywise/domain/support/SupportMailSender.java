package com.axel.pennywise.domain.support;

import java.util.UUID;

public interface SupportMailSender {
  void send(UUID userId, String userEmail, String subject, String message, String idempotencyKey);
}
