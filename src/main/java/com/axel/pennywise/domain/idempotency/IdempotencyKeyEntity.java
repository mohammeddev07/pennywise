package com.axel.pennywise.domain.idempotency;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKeyEntity {

  @EmbeddedId private IdempotencyKeyId id;

  @Column(nullable = false)
  private String requestHash;

  @Column private Integer responseCode;

  @Column(columnDefinition = "text")
  private String responseBody;

  @Column(nullable = false, columnDefinition = "timestamptz")
  private OffsetDateTime createdAt = OffsetDateTime.now();
}
