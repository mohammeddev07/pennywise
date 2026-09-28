package com.axel.pennywise.domain.book;

import static org.junit.jupiter.api.Assertions.*;

import com.axel.pennywise.domain.summary.SummaryService;
import com.axel.pennywise.domain.transaction.query.AbstractPostgresIT;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserRepository;
import com.axel.pennywise.exception.ApiException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class BookManagementServiceTest extends AbstractPostgresIT {
  @Autowired BookService service;
  @Autowired BookRepository books;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired SummaryService summaries;
  @Autowired PlatformTransactionManager transactions;
  UserEntity user;

  @BeforeEach
  void seed() {
    user = new UserEntity();
    user.setAuthSubject(UUID.randomUUID().toString());
    user = users.saveAndFlush(user);
  }

  BookEntity create(long opening) {
    return service.create(user, "  Book  ", "usd", "UTC", opening);
  }

  void error(String code, Runnable action) {
    assertEquals(code, assertThrows(ApiException.class, action::run).code());
  }

  @Test
  void styleVocabularyAndLegacyValidation() {
    for (String icon : BookStyles.ICONS) BookStyles.validate(icon, "green");
    for (String color : BookStyles.COLORS) BookStyles.validate("book", color);
    for (String bad : List.of("", " ", "BOOK", "...", "unknown", "#16A34A")) {
      error("VALIDATION_ERROR", () -> BookStyles.validate(bad, "green"));
      error("VALIDATION_ERROR", () -> BookStyles.validate("book", bad));
    }
    for (String bad : List.of("", "   ", "x".repeat(81)))
      error("VALIDATION_ERROR", () -> service.create(user, bad, "USD", "UTC", 0));
    for (String bad : List.of("", "   ", "US", "USDD"))
      error("VALIDATION_ERROR", () -> service.create(user, "Book", bad, "UTC", 0));
    error("VALIDATION_ERROR", () -> service.create(user, "Book", "USD", "Invalid/Zone", 0));
    assertEquals("x".repeat(80), service.create(user, "x".repeat(80), "zzz", "UTC", 0).getName());
    assertEquals("  Book  ", create(-20).getName());
    assertEquals("usd", create(0).getCurrencyCode());
  }

  @Test
  void orderingStylesOwnershipAndDeletion() {
    var a = create(100);
    var b = create(200);
    assertEquals("book", a.getIcon());
    assertEquals("green", a.getColor());
    service.reorder(user, List.of(b.getId(), a.getId()));
    assertEquals(
        List.of(b.getId(), a.getId()),
        service.listWithBalances(user).stream().map(x -> x.book().getId()).toList());
    var fresh = service.requireOwned(a.getId(), user);
    for (String icon : BookStyles.ICONS) {
      fresh = service.update(user, a.getId(), fresh.getVersion(), null, icon, null);
      assertEquals(icon, fresh.getIcon());
    }
    for (String color : BookStyles.COLORS) {
      fresh = service.update(user, a.getId(), fresh.getVersion(), null, null, color);
      assertEquals(color, fresh.getColor());
    }
    var stranger = new UserEntity();
    stranger.setAuthSubject(UUID.randomUUID().toString());
    stranger = users.saveAndFlush(stranger);
    UserEntity other = stranger;
    error("NOT_FOUND", () -> service.requireOwned(a.getId(), other));
    error("NOT_FOUND", () -> service.update(other, a.getId(), 0, "Other", null, null));
    error("NOT_FOUND", () -> service.delete(other, a.getId(), 0));
    assertTrue(service.listWithBalances(other).isEmpty());
    service.delete(user, a.getId(), fresh.getVersion());
    error("NOT_FOUND", () -> service.delete(user, a.getId(), 0));
    error(
        "LAST_BOOK_REQUIRED",
        () -> service.delete(user, b.getId(), service.requireOwned(b.getId(), user).getVersion()));
    assertEquals(1, service.listWithBalances(user).size());
  }

  @Test
  void rejectsInvalidOrdersAtomically() {
    var a = create(0);
    var b = create(0);
    error("VALIDATION_ERROR", () -> service.reorder(user, List.of(a.getId())));
    error("VALIDATION_ERROR", () -> service.reorder(user, List.of(a.getId(), a.getId())));
    error(
        "VALIDATION_ERROR",
        () -> service.reorder(user, List.of(a.getId(), b.getId(), UUID.randomUUID())));
    error("NOT_FOUND", () -> service.reorder(user, List.of(a.getId(), UUID.randomUUID())));
    assertEquals(
        List.of(a.getId(), b.getId()),
        service.listWithBalances(user).stream().map(x -> x.book().getId()).toList());
  }

  @Test
  void groupedBalancesAndLargeOpeningCharacterization() {
    var a = create(1L << 53);
    var b = create(-10);
    assertEquals(9007199254740992L, service.listWithBalances(user).getFirst().balanceMinor());
    assertEquals(9007199254740992L, summaries.balance(a).balanceMinor());
    assertEquals(0, summaries.monthly(a, YearMonth.of(2026, 1)).incomeTotalMinor());
    assertEquals(
        0,
        summaries
            .range(a, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
            .expenseTotalMinor());
    UUID category =
        jdbc.queryForObject(
            "select id from expense_tracker.categories where book_id=? limit 1",
            UUID.class,
            b.getId());
    jdbc.update(
        "insert into"
            + " expense_tracker.transactions(book_id,category_id,type,amount_minor,occurred_on)"
            + " values (?,?,'INCOME',50,'2026-01-01'), (?,?,'EXPENSE',15,'2026-01-01')",
        b.getId(),
        category,
        b.getId(),
        category);
    assertEquals(25L, service.listWithBalances(user).get(1).balanceMinor());
    service.delete(user, b.getId(), b.getVersion());
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from expense_tracker.transactions where book_id=?",
            Integer.class,
            b.getId()));
    assertEquals(1, service.listWithBalances(user).size());
  }

  @Test
  void capAndExistingUsersOverCap() throws Exception {
    for (int i = 0; i < 9; i++) create(0);
    List<String> outcomes = race(() -> createOutcome(), () -> createOutcome());
    assertEquals(1, outcomes.stream().filter("OK"::equals).count());
    assertEquals(1, outcomes.stream().filter("BOOK_LIMIT_REACHED"::equals).count());
    jdbc.update(
        "insert into expense_tracker.books(owner_user_id,name,currency_code,timezone) values"
            + " (?,'Legacy','USD','UTC')",
        user.getId());
    assertEquals(11, service.listWithBalances(user).size());
    error("BOOK_LIMIT_REACHED", () -> create(0));
  }

  String createOutcome() {
    try {
      create(0);
      return "OK";
    } catch (ApiException ex) {
      return ex.code();
    }
  }

  @Test
  void concurrentDeletionLeavesOneBook() throws Exception {
    var a = create(0);
    var b = create(0);
    var outcomes = race(() -> deleteOutcome(a), () -> deleteOutcome(b));
    assertTrue(outcomes.contains("OK"));
    assertTrue(outcomes.contains("LAST_BOOK_REQUIRED"));
    assertEquals(1, service.listWithBalances(user).size());
  }

  String deleteOutcome(BookEntity b) {
    try {
      service.delete(user, b.getId(), b.getVersion());
      return "OK";
    } catch (ApiException ex) {
      return ex.code();
    }
  }

  @Test
  void serializedReorderLastWriterWinsAndMembershipChangesFailClearly() throws Exception {
    var a = create(0);
    var b = create(0);
    orderedRace(
        () -> service.reorder(user, List.of(b.getId(), a.getId())),
        () -> service.reorder(user, List.of(a.getId(), b.getId())));
    assertEquals(
        List.of(a.getId(), b.getId()),
        service.listWithBalances(user).stream().map(x -> x.book().getId()).toList());
    orderedRace(
        () -> create(0),
        () ->
            error("VALIDATION_ERROR", () -> service.reorder(user, List.of(a.getId(), b.getId()))));
    var ids = service.listWithBalances(user).stream().map(x -> x.book().getId()).toList();
    orderedRace(
        () -> service.delete(user, b.getId(), service.requireOwned(b.getId(), user).getVersion()),
        () -> error("VALIDATION_ERROR", () -> service.reorder(user, ids)));
    assertEquals(2, service.listWithBalances(user).size());
  }

  @Test
  void reorderBeforeMembershipChangesSucceedsWithoutPartialState() throws Exception {
    var a = create(0);
    var b = create(0);
    orderedRace(() -> service.reorder(user, List.of(b.getId(), a.getId())), () -> create(0));
    var listed = service.listWithBalances(user);
    assertEquals(
        List.of(b.getId(), a.getId()),
        listed.subList(0, 2).stream().map(x -> x.book().getId()).toList());
    var c = listed.get(2).book();
    orderedRace(
        () -> service.reorder(user, List.of(c.getId(), b.getId(), a.getId())),
        () -> service.delete(user, a.getId(), service.requireOwned(a.getId(), user).getVersion()));
    assertEquals(
        List.of(c.getId(), b.getId()),
        service.listWithBalances(user).stream().map(x -> x.book().getId()).toList());
    error("ETAG_MISMATCH", () -> service.update(user, b.getId(), 0, "Stale", null, null));
  }

  void orderedRace(Runnable first, Runnable second) throws Exception {
    var locked = new CountDownLatch(1);
    var attempted = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var f =
          pool.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .executeWithoutResult(
                          tx -> {
                            users.lockActiveById(user.getId());
                            first.run();
                            locked.countDown();
                            try {
                              assertTrue(attempted.await(10, TimeUnit.SECONDS));
                            } catch (InterruptedException e) {
                              throw new RuntimeException(e);
                            }
                          }));
      var s =
          pool.submit(
              () -> {
                try {
                  assertTrue(locked.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
                attempted.countDown();
                second.run();
              });
      f.get(20, TimeUnit.SECONDS);
      s.get(20, TimeUnit.SECONDS);
    }
  }

  List<String> race(Callable<String> first, Callable<String> second) throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(first);
      var b = pool.submit(second);
      return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
    }
  }
}
