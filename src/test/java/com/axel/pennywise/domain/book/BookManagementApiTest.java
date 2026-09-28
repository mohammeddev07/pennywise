package com.axel.pennywise.domain.book;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.axel.pennywise.domain.transaction.query.AbstractPostgresIT;
import com.axel.pennywise.domain.user.*;
import com.fasterxml.jackson.databind.*;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "app.security.auth-enabled=true",
      "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.axel.pennywise.domain.book.BookManagementApiTest$Sql"
    })
class BookManagementApiTest extends AbstractPostgresIT {
  public static class Sql implements StatementInspector {
    static final List<String> statements = new CopyOnWriteArrayList<>();

    public String inspect(String sql) {
      statements.add(sql);
      return sql;
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;
  @Autowired UserRepository users;
  @Autowired BookService books;
  @Autowired JdbcTemplate jdbc;
  @Autowired CacheManager caches;
  @Autowired PlatformTransactionManager transactions;
  UserEntity user;
  String subject;

  @BeforeEach
  void setup() {
    subject = "book-api-" + UUID.randomUUID();
    user = new UserEntity();
    user.setAuthSubject(subject);
    user = users.saveAndFlush(user);
    caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
  }

  RequestPostProcessor auth() {
    return jwt().jwt(j -> j.subject(subject));
  }

  JsonNode response(ResultActions action) throws Exception {
    return om.readTree(action.andReturn().getResponse().getContentAsString());
  }

  JsonNode create(long opening) throws Exception {
    return response(
        mvc.perform(
                post("/v1/books")
                    .with(auth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        om.writeValueAsString(
                            Map.of(
                                "name",
                                "Book",
                                "currencyCode",
                                "USD",
                                "timezone",
                                "UTC",
                                "openingBalanceMinor",
                                opening))))
            .andExpect(status().isCreated()));
  }

  JsonNode list() throws Exception {
    return response(mvc.perform(get("/v1/books").with(auth())).andExpect(status().isOk()))
        .get("items");
  }

  String path(JsonNode b) {
    return "/v1/books/" + b.get("id").asText();
  }

  void warm(JsonNode b) throws Exception {
    books.list(user);
    list();
    for (String suffix :
        List.of(
            "/balance", "/summary/monthly?month=2026-01", "/categories", "/budgets?month=2026-01"))
      mvc.perform(get(path(b) + suffix).with(auth())).andExpect(status().isOk());
  }

  JsonNode patchBook(JsonNode b, String body) throws Exception {
    return response(
        mvc.perform(
                patch(path(b))
                    .with(auth())
                    .header("If-Match", b.get("version").asText())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk()));
  }

  @Test
  void listHasFixedQueryCostAtOneAndTenBooks() throws Exception {
    create(0);
    Sql.statements.clear();
    list();
    var one = List.copyOf(Sql.statements);
    for (int i = 1; i < 10; i++) create(i);
    caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
    Sql.statements.clear();
    list();
    var ten = List.copyOf(Sql.statements);
    assertEquals(one.size(), ten.size());
    assertEquals(3, ten.size(), ten.toString());
    assertEquals(1, ten.stream().filter(s -> s.contains("GROUP BY b.id")).count());
  }

  @Test
  void warmedCachesReflectBookMutationsAndRollback() throws Exception {
    var a = create(10);
    warm(a);
    var b = create(20);
    assertEquals(2, list().size());
    warm(a);
    a = patchBook(a, "{\"name\":\"Renamed\",\"icon\":\"car\",\"color\":\"pink\"}");
    assertEquals("Renamed", list().get(0).get("name").asText());
    assertEquals("car", list().get(0).get("icon").asText());
    warm(a);
    mvc.perform(
            put("/v1/books/order")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    om.writeValueAsString(
                        Map.of("bookIds", List.of(b.get("id").asText(), a.get("id").asText())))))
        .andExpect(status().isNoContent());
    assertEquals(b.get("id"), list().get(0).get("id"));
    warm(b);
    var rollbackBook = b;
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              books.update(
                  user,
                  UUID.fromString(rollbackBook.get("id").asText()),
                  1,
                  "Rollback",
                  null,
                  null);
              tx.setRollbackOnly();
            });
    assertEquals("Book", list().get(0).get("name").asText());
    assertEquals("Book", books.list(user).get(0).getName());
    var fresh = response(mvc.perform(get(path(b)).with(auth())).andExpect(status().isOk()));
    mvc.perform(delete(path(b)).with(auth()).header("If-Match", fresh.get("version").asText()))
        .andExpect(status().isNoContent());
    assertEquals(1, list().size());
    assertEquals(1, books.list(user).size());
  }

  @Test
  void warmedCachesReflectTransactionCreateEditDeleteAndImport() throws Exception {
    var b = create(100);
    warm(b);
    String category =
        jdbc.queryForObject(
                "select id from expense_tracker.categories where book_id=? and type='INCOME' limit"
                    + " 1",
                UUID.class,
                UUID.fromString(b.get("id").asText()))
            .toString();
    var tx =
        response(
            mvc.perform(
                    post(path(b) + "/transactions")
                        .with(auth())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            om.writeValueAsString(
                                Map.of(
                                    "type",
                                    "INCOME",
                                    "amountMinor",
                                    50,
                                    "occurredOn",
                                    "2026-01-01",
                                    "categoryId",
                                    category))))
                .andExpect(status().isCreated()));
    assertBalance(b, 150);
    warm(b);
    tx =
        response(
            mvc.perform(
                    patch(path(b) + "/transactions/" + tx.get("id").asText())
                        .with(auth())
                        .header("If-Match", tx.get("version").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":80}"))
                .andExpect(status().isOk()));
    assertBalance(b, 180);
    warm(b);
    mvc.perform(
            delete(path(b) + "/transactions/" + tx.get("id").asText())
                .with(auth())
                .header("If-Match", tx.get("version").asText()))
        .andExpect(status().isNoContent());
    assertBalance(b, 100);
    warm(b);
    mvc.perform(multipart(path(b) + "/transactions/import").file(importFile()).with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.importedCount").value(1));
    assertBalance(b, 350);
  }

  void assertBalance(JsonNode b, long expected) throws Exception {
    assertEquals(expected, list().get(0).get("balanceMinor").asLong());
    mvc.perform(get(path(b) + "/balance").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balanceMinor").value(expected));
  }

  MockMultipartFile importFile() throws Exception {
    try (var wb = new XSSFWorkbook();
        var out = new ByteArrayOutputStream()) {
      var sheet = wb.createSheet("Transactions");
      var h = sheet.createRow(0);
      String[] headers = {"Date", "Description", "Amount", "Type", "Category"};
      for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
      var row = sheet.createRow(1);
      row.createCell(0).setCellValue("2026-01-01");
      row.createCell(1).setCellValue("Imported");
      row.createCell(2).setCellValue("2.50");
      row.createCell(3).setCellValue("Income");
      row.createCell(4).setCellValue("Salary");
      wb.write(out);
      return new MockMultipartFile(
          "file",
          "transactions.xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          out.toByteArray());
    }
  }

  @Test
  void validationAndLargeOpeningRemainCompatible() throws Exception {
    var b = create(1L << 53);
    assertBalance(b, 9007199254740992L);
    mvc.perform(
            post("/v1/books")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \",\"currencyCode\":\"USD\",\"timezone\":\"UTC\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    for (String currency : List.of("usd", "zzz"))
      mvc.perform(
              post("/v1/books")
                  .with(auth())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      om.writeValueAsString(
                          Map.of(
                              "name",
                              "  duplicate  ",
                              "currencyCode",
                              currency,
                              "timezone",
                              "UTC"))))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.name").value("  duplicate  "))
          .andExpect(jsonPath("$.currencyCode").value(currency))
          .andExpect(jsonPath("$.openingBalanceMinor").value(0));
    for (String field : List.of("icon", "color"))
      for (String bad : List.of("unknown", "BOOK", "...", "#16A34A", "")) {
        mvc.perform(
                post("/v1/books")
                    .with(auth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        om.writeValueAsString(
                            Map.of(
                                "name",
                                "Book",
                                "currencyCode",
                                "USD",
                                "timezone",
                                "UTC",
                                field,
                                bad))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
      }
  }

  @Test
  void versionsNoopAndLastBookErrors() throws Exception {
    var b = create(0);
    var unchanged = patchBook(b, "{\"name\":\"Book\"}");
    assertEquals(b.get("version"), unchanged.get("version"));
    assertEquals(
        java.time.OffsetDateTime.parse(b.get("updatedAt").asText()).toInstant(),
        java.time.OffsetDateTime.parse(unchanged.get("updatedAt").asText()).toInstant());
    var changed = patchBook(b, "{\"color\":\"blue\"}");
    assertEquals(1, changed.get("version").asLong());
    mvc.perform(
            patch(path(b))
                .with(auth())
                .header("If-Match", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"stale\"}"))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.error.code").value("ETAG_MISMATCH"));
    mvc.perform(delete(path(b)).with(auth()).header("If-Match", "1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("LAST_BOOK_REQUIRED"));
  }

  @Test
  void foreignBooksAreHiddenByEveryManagementEndpoint() throws Exception {
    var owned = create(0);
    String firstSubject = subject;
    subject = "foreign-" + UUID.randomUUID();
    var foreign = create(0);
    subject = firstSubject;
    mvc.perform(get(path(foreign)).with(auth()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mvc.perform(
            patch(path(foreign))
                .with(auth())
                .header("If-Match", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Other\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mvc.perform(delete(path(foreign)).with(auth()).header("If-Match", "0"))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/v1/books/order")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    om.writeValueAsString(Map.of("bookIds", List.of(foreign.get("id").asText())))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    assertEquals(owned.get("id"), list().get(0).get("id"));
  }
}
