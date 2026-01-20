package com.axel.pennywise.domain.book;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookRepository extends JpaRepository<BookEntity, UUID> {
    List<BookEntity> findAllByOwner_IdAndDeletedAtIsNull(UUID ownerUserId);
    Optional<BookEntity> findByIdAndOwner_IdAndDeletedAtIsNull(UUID id, UUID ownerUserId);
}
