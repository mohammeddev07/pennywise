package com.axel.pennywise.domain.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookRepository;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.category.CategoryType;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * P1.1 record-history invariants, exercised through the real HTTP stack against PostgreSQL: the
 * Flyway trigger, Hibernate's updatable=false mapping, optimistic locking, PATCH presence
 * semantics and book ownership. Timestamps are compared as {@link Instant}s read back from the
 * database (microsecond precision), never as strings, so offset spelling cannot mask a change.
 *
 * <p>Auth is switched on here (unlike the rest of the suite) so two distinct JWT subjects map to
 * two distinct users and cross-tenant access can be asserted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "app.security.auth-enabled=true")
class TransactionInvariantsIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("pennywise_test")
          .withUsername("postgres")
          .withPassword("postgres");

  private static final String USER_A = "p11-user-a";
  private static final String USER_B = "p11-user-b";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;
  @Autowired private UserRepository userRepo;
  @Autowired private BookRepository bookRepo;
  @Autowired private CategoryRepository categoryRepo;
  @Autowired private TransactionRepository txRepo;
  @Autowired private JdbcTemplate jdbc;

  private UUID bookA;
  private UUID categoryA;

  @BeforeEach
  void seed() {
    // Fresh subjects per test: each user/book is isolated, no cleanup needed.
    String suffix = UUID.randomUUID().toString();
    UserEntity userA = user(USER_A + "-" + suffix);
    user(USER_B + "-" + suffix);
    subjectA = userA.getAuthSubject();
    subjectB = USER_B + "-" + suffix;

    BookEntity book = new BookEntity();
    book.setOwner(userA);
    book.setName("Invariants");
    book.setCurrencyCode("USD");
    book.setTimezone("America/Chicago"); // UTC-6 in winter: month boundary tests rely on this
    book.setOpeningBalanceMinor(0L);
    // save() merges (version is non-null), so the managed copy is the one to reference.
    book = bookRepo.save(book);
    bookA = book.getId();

    CategoryEntity category = new CategoryEntity();
    category.setBook(book);
    category.setType(CategoryType.EXPENSE);
    category.setName("Groceries");
    categoryA = categoryRepo.save(category).getId();
  }

  private String subjectA;
  private String subjectB;

  private UserEntity user(String subject) {
    UserEntity u = new UserEntity();
    u.setAuthSubject(subject);
    return userRepo.save(u);
  }

  private RequestPostProcessor as(String subject) {
    return jwt().jwt(j -> j.subject(subject));
  }

  private String txUrl(UUID txId) {
    return "/v1/books/" + bookA + "/transactions/" + txId;
  }

  private JsonNode create(String json) throws Exception {
    MvcResult res =
        mvc.perform(
                post("/v1/books/" + bookA + "/transactions")
                    .with(as(subjectA))
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    return om.readTree(res.getResponse().getContentAsString());
  }

  private JsonNode createDefault() throws Exception {
    return create(
        "{\"type\":\"EXPENSE\",\"amountMinor\":1500,\"categoryId\":\""
            + categoryA
            + "\",\"occurredAt\":\"2026-02-01T04:30:00Z\",\"title\":\"Groceries run\","
            + "\"note\":\"weekly\",\"paymentMethod\":\"CARD\"}");
  }

  private MockHttpServletRequestBuilder patchAs(
      String subject, UUID txId, long ifMatchVersion, String json) {
    return patch(txUrl(txId))
        .with(as(subject))
        .header("If-Match", "\"" + ifMatchVersion + "\"")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private TransactionEntity reload(UUID txId) {
    return txRepo.findById(txId).orElseThrow();
  }

  private static Instant instant(JsonNode node, String field) {
    return OffsetDateTime.parse(node.get(field).asText()).toInstant();
  }

  // ---------------------------------------------------------------------------------------------

  @Test
  void createEditReload_preservesCreatedAt_advancesUpdatedAtAndVersion() throws Exception {
    JsonNode created = createDefault();
    UUID txId = UUID.fromString(created.get("id").asText());
    assertEquals("2026-01-31", created.get("occurredOn").asText(), "derived in America/Chicago");
    assertEquals(0, created.get("version").asLong());
    assertTrue(created.get("externalId").isNull(), "externalId is exposed read-only");

    TransactionEntity before = reload(txId);
    Instant createdAt0 = before.getCreatedAt().toInstant();
    Instant updatedAt0 = before.getUpdatedAt().toInstant();
    assertEquals(createdAt0, instant(created, "createdAt"));

    Thread.sleep(5); // DB precision is microseconds; make "advances" unambiguous

    MvcResult res =
        mvc.perform(patchAs(subjectA, txId, 0, "{\"title\":\"Edited\",\"amountMinor\":1700}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Edited"))
            .andExpect(jsonPath("$.amountMinor").value(1700))
            .andExpect(jsonPath("$.version").value(1))
            .andReturn();
    assertEquals("\"1\"", res.getResponse().getHeader("ETag"));
    JsonNode edited = om.readTree(res.getResponse().getContentAsString());

    // Response carries the committed values, not the pre-flush entity state.
    assertEquals(createdAt0, instant(edited, "createdAt"));
    assertTrue(instant(edited, "updatedAt").isAfter(updatedAt0));

    TransactionEntity after = reload(txId);
    assertEquals(txId, after.getId());
    assertEquals(createdAt0, after.getCreatedAt().toInstant());
    assertTrue(after.getUpdatedAt().toInstant().isAfter(updatedAt0));
    assertEquals(1L, after.getVersion());
    assertEquals(instant(edited, "updatedAt"), after.getUpdatedAt().toInstant());
  }

  @Test
  void staleIfMatch_isRejected_andRowIsUntouched() throws Exception {
    UUID txId = UUID.fromString(createDefault().get("id").asText());
    mvc.perform(patchAs(subjectA, txId, 0, "{\"title\":\"first\"}")).andExpect(status().isOk());
    TransactionEntity v1 = reload(txId);

    mvc.perform(patchAs(subjectA, txId, 0, "{\"title\":\"stale\"}"))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.error.code").value("ETAG_MISMATCH"));

    TransactionEntity still = reload(txId);
    assertEquals("first", still.getTitle());
    assertEquals(1L, still.getVersion());
    assertEquals(v1.getUpdatedAt().toInstant(), still.getUpdatedAt().toInstant());
  }

  @Test
  void directSqlCreatedAtMutation_isRejectedByTrigger() throws Exception {
    UUID txId = UUID.fromString(createDefault().get("id").asText());
    Instant createdAt0 = reload(txId).getCreatedAt().toInstant();

    DataAccessException ex =
        assertThrows(
            DataAccessException.class,
            () ->
                jdbc.update(
                    "update expense_tracker.transactions set created_at = created_at + interval"
                        + " '1 day' where id = ?",
                    txId));
    assertTrue(ex.getMessage().contains("created_at is immutable"), ex.getMessage());
    assertEquals(createdAt0, reload(txId).getCreatedAt().toInstant());

    // IS DISTINCT FROM: writing the identical value is not a change and still passes.
    assertEquals(
        1,
        jdbc.update(
            "update expense_tracker.transactions set created_at = created_at, title = 'sql'"
                + " where id = ?",
            txId));
    assertEquals("sql", reload(txId).getTitle());
  }

  @Test
  void explicitNull_clearsNullableFields_omittedLeavesThem() throws Exception {
    UUID txId = UUID.fromString(createDefault().get("id").asText());

    mvc.perform(patchAs(subjectA, txId, 0, "{\"title\":null,\"paymentMethod\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value((Object) null))
        .andExpect(jsonPath("$.paymentMethod").value((Object) null))
        .andExpect(jsonPath("$.note").value("weekly"));

    TransactionEntity row = reload(txId);
    assertNull(row.getTitle());
    assertNull(row.getPaymentMethod());
    assertEquals("weekly", row.getNote(), "omitted note is unchanged");

    // Blank string clears too (explicit normalization).
    mvc.perform(patchAs(subjectA, txId, 1, "{\"note\":\"   \"}")).andExpect(status().isOk());
    assertNull(reload(txId).getNote());
  }

  @Test
  void explicitNull_onRequiredField_isRejected() throws Exception {
    UUID txId = UUID.fromString(createDefault().get("id").asText());

    mvc.perform(patchAs(subjectA, txId, 0, "{\"amountMinor\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.details[0].field").value("amountMinor"));

    assertEquals(0L, reload(txId).getVersion());
  }

  @Test
  void auditFieldsInBody_areRejected_andNeverAlterTheRecord() throws Exception {
    JsonNode created = createDefault();
    UUID txId = UUID.fromString(created.get("id").asText());
    TransactionEntity before = reload(txId);

    for (String field : new String[] {"createdAt", "updatedAt", "version", "id", "externalId"}) {
      mvc.perform(
              patchAs(subjectA, txId, 0, "{\"title\":\"x\",\"" + field + "\":\"2000-01-01T00:00:00Z\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
          .andExpect(jsonPath("$.error.details[0].field").value(field));
    }

    mvc.perform(
            post("/v1/books/" + bookA + "/transactions")
                .with(as(subjectA))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"EXPENSE\",\"amountMinor\":1,\"categoryId\":\""
                        + categoryA
                        + "\",\"occurredOn\":\"2026-01-01\",\"createdAt\":\"2000-01-01T00:00:00Z\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details[0].field").value("createdAt"));

    TransactionEntity after = reload(txId);
    assertEquals("Groceries run", after.getTitle());
    assertEquals(0L, after.getVersion());
    assertEquals(before.getCreatedAt().toInstant(), after.getCreatedAt().toInstant());
    assertEquals(before.getUpdatedAt().toInstant(), after.getUpdatedAt().toInstant());
  }

  @Test
  void noOpPatch_keepsUpdatedAtAndVersion() throws Exception {
    JsonNode created = createDefault();
    UUID txId = UUID.fromString(created.get("id").asText());
    TransactionEntity before = reload(txId);
    Thread.sleep(5);

    // Same values as stored (occurredAt echoed back as the UTC instant the server holds).
    mvc.perform(
            patchAs(
                subjectA,
                txId,
                0,
                "{\"title\":\"Groceries run\",\"amountMinor\":1500,\"occurredOn\":\"2026-01-31\","
                    + "\"occurredAt\":\"2026-02-01T04:30:00Z\",\"paymentMethod\":\"CARD\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0));

    TransactionEntity after = reload(txId);
    assertEquals(0L, after.getVersion());
    assertEquals(before.getUpdatedAt().toInstant(), after.getUpdatedAt().toInstant());
    assertEquals(before.getCreatedAt().toInstant(), after.getCreatedAt().toInstant());
  }

  @Test
  void anotherUsersBook_isNotFound() throws Exception {
    UUID txId = UUID.fromString(createDefault().get("id").asText());

    mvc.perform(get(txUrl(txId)).with(as(subjectB))).andExpect(status().isNotFound());
    mvc.perform(get("/v1/books/" + bookA + "/transactions").with(as(subjectB)))
        .andExpect(status().isNotFound());
    mvc.perform(patchAs(subjectB, txId, 0, "{\"title\":\"hijack\"}"))
        .andExpect(status().isNotFound());

    assertEquals("Groceries run", reload(txId).getTitle());
    assertEquals(0L, reload(txId).getVersion());
  }

  @Test
  void dateEdits_useBookLocalOccurredOn_acrossMonthBoundary() throws Exception {
    UUID txId = UUID.fromString(createDefault().get("id").asText());
    // 2026-02-01T04:30Z is 2026-01-31 22:30 in Chicago -> January in the ledger.
    assertEquals(LocalDate.of(2026, 1, 31), reload(txId).getOccurredOn());
    assertEquals(1, txRepo.countForRange(bookA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)));
    assertEquals(0, txRepo.countForRange(bookA, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)));

    // Both supplied and disagreeing in the book timezone: rejected, row untouched.
    mvc.perform(
            patchAs(
                subjectA, txId, 0, "{\"occurredOn\":\"2026-02-01\",\"occurredAt\":\"2026-02-01T04:30:00Z\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    assertEquals(LocalDate.of(2026, 1, 31), reload(txId).getOccurredOn());
    assertEquals(0L, reload(txId).getVersion());

    // Both supplied and agreeing: accepted.
    mvc.perform(
            patchAs(
                subjectA, txId, 0, "{\"occurredOn\":\"2026-01-31\",\"occurredAt\":\"2026-02-01T05:00:00Z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.occurredOn").value("2026-01-31"));
    assertEquals(
        Instant.parse("2026-02-01T05:00:00Z"), reload(txId).getOccurredAt().toInstant());

    // occurredOn alone moves the ledger day; occurredAt is reset to book-local midnight.
    mvc.perform(patchAs(subjectA, txId, 1, "{\"occurredOn\":\"2026-02-01\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.occurredOn").value("2026-02-01"));
    TransactionEntity moved = reload(txId);
    assertEquals(LocalDate.of(2026, 2, 1), moved.getOccurredOn());
    assertEquals(Instant.parse("2026-02-01T06:00:00Z"), moved.getOccurredAt().toInstant());
    assertEquals(0, txRepo.countForRange(bookA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)));
    assertEquals(1, txRepo.countForRange(bookA, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)));

    // occurredAt alone derives the ledger day in the book timezone (offset spelling irrelevant).
    mvc.perform(patchAs(subjectA, txId, 2, "{\"occurredAt\":\"2026-03-01T01:30:00+02:00\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.occurredOn").value("2026-02-28"));
    assertEquals(
        Instant.parse("2026-02-28T23:30:00Z"), reload(txId).getOccurredAt().toInstant());
    assertNotEquals(0L, reload(txId).getVersion());
  }
}
