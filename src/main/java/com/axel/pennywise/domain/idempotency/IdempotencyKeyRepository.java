package com.axel.pennywise.domain.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository
    extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyId> {}
