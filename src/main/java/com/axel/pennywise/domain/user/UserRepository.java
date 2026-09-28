package com.axel.pennywise.domain.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select u from UserEntity u where u.id = :id and u.deletedAt is null")
  Optional<UserEntity> lockActiveById(
      @org.springframework.data.repository.query.Param("id") UUID id);

  Optional<UserEntity> findByAuthSubjectAndDeletedAtIsNull(String authSubject);

  Optional<UserEntity> findByGoogleSubAndDeletedAtIsNull(String googleSub);

  boolean existsByEmailAndDeletedAtIsNull(String email);

  boolean existsByAuthSubjectAndDeletedAtIsNull(String authSubject);
}
