package com.axel.pennywise.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@MappedSuperclass
@Getter
@Setter
public abstract class AuditedEntity {

    @Column(nullable = false, columnDefinition = "timestamptz")
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
