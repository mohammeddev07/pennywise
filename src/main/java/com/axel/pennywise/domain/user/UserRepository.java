package com.axel.pennywise.domain.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  Optional<UserEntity> findByAuthSubjectAndDeletedAtIsNull(String authSubject);

  boolean existsByAuthSubjectAndDeletedAtIsNull(String authSubject);
}
