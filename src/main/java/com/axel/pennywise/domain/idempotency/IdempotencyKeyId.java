package com.axel.pennywise.domain.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.Getter;

@Getter
@Embeddable
public class IdempotencyKeyId implements Serializable {
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "idem_key", nullable = false)
  private String key;

  protected IdempotencyKeyId() {}

  public IdempotencyKeyId(UUID userId, String key) {
    this.userId = userId;
    this.key = key;
  }
}
