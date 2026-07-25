package com.axel.pennywise.domain.export;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportJobRepository extends JpaRepository<ExportJobEntity, UUID> {
  Optional<ExportJobEntity> findByIdAndBook_IdAndDeletedAtIsNull(UUID id, UUID bookId);
}
