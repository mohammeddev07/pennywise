package com.axel.pennywise.domain.export;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExportJobRepository extends JpaRepository<ExportJobEntity, UUID> {
    Optional<ExportJobEntity> findByIdAndBook_IdAndDeletedAtIsNull(UUID id, UUID bookId);
}
