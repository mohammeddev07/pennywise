package com.axel.pennywise.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Audit columns shared by every persisted row.
 *
 * <p>{@code createdAt} is server-controlled and immutable: {@link #prePersist()} overwrites
 * whatever a caller set, and the column is mapped {@code updatable = false}, so a later {@code
 * setCreatedAt(...)} on a managed entity is never written back. The setter stays for test fixtures
 * and never reaches the database on an UPDATE. For {@code transactions} the same rule is also
 * enforced by a database trigger (V6). {@code updatedAt} and {@code version} advance only when a
 * flush actually writes a changed row; a no-op save leaves both untouched.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditedEntity {

  @Column(nullable = false, updatable = false, columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;

  @Column(nullable = false, columnDefinition = "timestamptz")
  private OffsetDateTime updatedAt;

  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime deletedAt;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  @PrePersist
  void prePersist() {
    OffsetDateTime now = OffsetDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = OffsetDateTime.now();
  }
}
