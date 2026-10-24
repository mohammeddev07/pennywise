package com.axel.pennywise.domain.user;

import com.axel.pennywise.exception.ApiException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class UserService {
  private final UserRepository repo;
  private final TransactionTemplate transactions;
  private final Cache<String, Snapshot> users;

  @Autowired
  public UserService(UserRepository repo, PlatformTransactionManager transactionManager) {
    this(repo, transactionManager, Ticker.systemTicker());
  }

  UserService(UserRepository repo, PlatformTransactionManager transactionManager, Ticker ticker) {
    this.repo = repo;
    this.transactions = new TransactionTemplate(transactionManager);
    this.users =
        Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofSeconds(60))
            .ticker(ticker)
            .build();
  }

  public UserEntity getOrCreate(Authentication auth, String subject, String email) {
    // A caller's transaction must see its own writes, and must not publish uncommitted entities.
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      return loadOrCreate(subject, email);
    }
    Snapshot snapshot = users.get(subject, key -> loadSnapshot(key, email));
    if (snapshot.email() == null && email != null && !email.isBlank()) {
      users.invalidate(subject);
      snapshot = users.get(subject, key -> loadSnapshot(key, email));
    }
    return snapshot.copy();
  }

  private Snapshot loadSnapshot(String subject, String email) {
    // Cache misses retain the original transaction boundary; hits never acquire a connection.
    UserEntity user = transactions.execute(status -> loadOrCreate(subject, email));
    return Snapshot.from(Objects.requireNonNull(user));
  }

  private UserEntity loadOrCreate(String subject, String email) {
    log.debug("Getting or creating user: subject={}, email={}", subject, email);
    return repo.findByAuthSubjectAndDeletedAtIsNull(subject)
        .map(
            existing -> {
              if (email != null && !email.isBlank() && existing.getEmail() == null) {
                existing.setEmail(email);
                return repo.save(existing);
              }
              return existing;
            })
        .orElseGet(
            () -> {
              log.info("Creating new user: subject={}, email={}", subject, email);
              UserEntity u = new UserEntity();
              u.setAuthSubject(subject);
              u.setEmail(email);
              UserEntity saved = repo.save(u);
              log.info("User created: userId={}, subject={}", saved.getId(), subject);
              return saved;
            });
  }

  public UserEntity require(UserEntity u) {
    if (u == null) {
      log.warn("User authorization failed: user is null");
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
    }
    return u;
  }

  @Transactional
  public UserEntity updateDefaultCurrency(UserEntity user, String currencyCode) {
    String normalized = null;
    if (currencyCode != null && !currencyCode.isBlank()) {
      normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
    }
    user.setDefaultCurrencyCode(normalized);
    UserEntity saved = repo.save(user);
    evictAfterCommit(user.getAuthSubject());
    return saved;
  }

  /** Trimmed; a blank name clears it. */
  public UserEntity updateDisplayName(UserEntity user, String displayName) {
    String trimmed = displayName == null ? "" : displayName.trim();
    user.setDisplayName(trimmed.isEmpty() ? null : trimmed);
    UserEntity saved = repo.save(user);
    evictAfterCommit(user.getAuthSubject());
    return saved;
  }

  /** Also used by account-link writes, which advance the user's optimistic-lock version. */
  public void evictAfterCommit(String subject) {
    if (subject == null) return;
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              users.invalidate(subject);
            }
          });
    } else {
      users.invalidate(subject);
    }
  }

  private record Snapshot(
      UUID id,
      String subject,
      String email,
      String passwordHash,
      String googleSub,
      String displayName,
      String currency,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt,
      Long version) {
    static Snapshot from(UserEntity user) {
      return new Snapshot(
          user.getId(),
          user.getAuthSubject(),
          user.getEmail(),
          user.getPasswordHash(),
          user.getGoogleSub(),
          user.getDisplayName(),
          user.getDefaultCurrencyCode(),
          user.getCreatedAt(),
          user.getUpdatedAt(),
          user.getDeletedAt(),
          user.getVersion());
    }

    UserEntity copy() {
      UserEntity user = new UserEntity();
      user.setId(id);
      user.setAuthSubject(subject);
      user.setEmail(email);
      user.setPasswordHash(passwordHash);
      user.setGoogleSub(googleSub);
      user.setDisplayName(displayName);
      user.setDefaultCurrencyCode(currency);
      user.setCreatedAt(createdAt);
      user.setUpdatedAt(updatedAt);
      user.setDeletedAt(deletedAt);
      user.setVersion(version);
      return user;
    }
  }
}
