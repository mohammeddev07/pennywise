package com.axel.pennywise.domain.book;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<BookEntity, UUID> {
  List<BookEntity> findAllByOwner_IdAndDeletedAtIsNull(UUID ownerUserId);

  Optional<BookEntity> findByIdAndOwner_IdAndDeletedAtIsNull(UUID id, UUID ownerUserId);
}
