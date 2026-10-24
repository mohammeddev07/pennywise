package com.axel.pennywise.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class UserServiceCacheTest {
  private final UserRepository repo = mock(UserRepository.class);
  private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
  private final AtomicLong nanos = new AtomicLong();
  private final UserService service = new UserService(repo, manager, nanos::get);
  private UserEntity stored;

  @BeforeEach
  void setUp() {
    stored = new UserEntity();
    stored.setId(UUID.randomUUID());
    stored.setAuthSubject("subject");
    stored.setEmail("user@lab.invalid");
    stored.setDefaultCurrencyCode("USD");
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(repo.findByAuthSubjectAndDeletedAtIsNull("subject")).thenReturn(Optional.of(stored));
    when(repo.save(any())).thenAnswer(call -> call.getArgument(0));
  }

  @AfterEach
  void clearThreadState() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  private UserEntity read() {
    return service.getOrCreate(null, "subject", "user@lab.invalid");
  }

  @Test
  void hitsAvoidBothTheQueryAndTransactionAndReturnIndependentEntities() {
    UserEntity first = read();
    first.setDefaultCurrencyCode("MUTATED");
    UserEntity second = read();
    assertThat(second).isNotSameAs(first);
    assertThat(second.getId()).isEqualTo(stored.getId());
    assertThat(second.getDefaultCurrencyCode()).isEqualTo("USD");
    verify(repo).findByAuthSubjectAndDeletedAtIsNull("subject");
    verify(manager).getTransaction(any());
    verify(manager).commit(any());
  }

  @Test
  void expiresAfterTheSpecifiedTtl() {
    read();
    stored.setDisplayName("Fresh");
    nanos.set(61_000_000_000L);
    assertThat(read().getDisplayName()).isEqualTo("Fresh");
    verify(repo, times(2)).findByAuthSubjectAndDeletedAtIsNull("subject");
  }

  @Test
  void currencyAndNameWritesInvalidateSnapshots() {
    UserEntity user = read();
    service.updateDefaultCurrency(user, "eur");
    stored.setDefaultCurrencyCode("EUR");
    assertThat(read().getDefaultCurrencyCode()).isEqualTo("EUR");
    service.updateDisplayName(read(), "  New name  ");
    stored.setDisplayName("New name");
    assertThat(read().getDisplayName()).isEqualTo("New name");
    verify(repo, times(3)).findByAuthSubjectAndDeletedAtIsNull("subject");
  }

  @Test
  void failedSaveDoesNotMutateACachedSnapshot() {
    UserEntity user = read();
    when(repo.save(any())).thenThrow(new IllegalStateException("DB failed"));
    assertThatThrownBy(() -> service.updateDefaultCurrency(user, "eur"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(read().getDefaultCurrencyCode()).isEqualTo("USD");
  }

  @Test
  void publicationWaitsForSuccessfulCommit() {
    doThrow(new TransactionSystemException("commit failed"))
        .doNothing()
        .when(manager)
        .commit(any());
    assertThatThrownBy(this::read).isInstanceOf(TransactionSystemException.class);
    read();
    read();
    verify(repo, times(2)).findByAuthSubjectAndDeletedAtIsNull("subject");
  }

  @Test
  void invalidationWaitsForCommitAndAlsoHandlesAccountLinkWrites() {
    read();
    stored.setGoogleSub("linked");
    stored.setVersion(7L);
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    service.evictAfterCommit("subject");
    var callbacks = TransactionSynchronizationManager.getSynchronizations();
    TransactionSynchronizationManager.setActualTransactionActive(false);
    assertThat(read().getGoogleSub()).isNull();
    callbacks.forEach(callback -> callback.afterCommit());
    assertThat(read().getGoogleSub()).isEqualTo("linked");
    assertThat(read().getVersion()).isEqualTo(7L);
  }

  @Test
  void callersTransactionBypassesTheCacheWithoutPublishingUncommittedState() {
    read();
    stored.setDisplayName("uncommitted");
    TransactionSynchronizationManager.setActualTransactionActive(true);
    assertThat(read().getDisplayName()).isEqualTo("uncommitted");
    TransactionSynchronizationManager.setActualTransactionActive(false);
    assertThat(read().getDisplayName()).isNull();
  }

  @Test
  void aNewEmailClaimStillFillsAPreviouslyMissingEmail() {
    stored.setEmail(null);
    service.getOrCreate(null, "subject", null);
    assertThat(read().getEmail()).isEqualTo("user@lab.invalid");
    verify(repo).save(stored);
  }

  @Test
  void simultaneousMissesShareOneCommittedLoad() throws Exception {
    try (var workers = Executors.newFixedThreadPool(8)) {
      var calls = new ArrayList<java.util.concurrent.Future<UserEntity>>();
      for (int i = 0; i < 8; i++) calls.add(workers.submit(this::read));
      var results = new ArrayList<UserEntity>();
      for (var call : calls) results.add(call.get(2, TimeUnit.SECONDS));
      assertThat(results).allSatisfy(user -> assertThat(user.getId()).isEqualTo(stored.getId()));
      assertThat(results.get(0)).isNotSameAs(results.get(1));
      verify(repo).findByAuthSubjectAndDeletedAtIsNull("subject");
      verify(manager).commit(any());
    }
  }
}
