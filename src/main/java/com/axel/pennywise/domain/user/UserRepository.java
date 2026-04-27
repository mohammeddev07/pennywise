package com.axel.pennywise.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByAuthSubjectAndDeletedAtIsNull(String authSubject);
    boolean existsByAuthSubjectAndDeletedAtIsNull(String authSubject);
}
