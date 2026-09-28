package com.axel.pennywise.api.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.axel.pennywise.api.dto.summary.BalanceResponse;
import com.axel.pennywise.domain.book.*;
import com.axel.pennywise.domain.summary.SummaryService;
import com.axel.pennywise.domain.user.*;
import com.axel.pennywise.exception.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BookControllerTest {
  @Mock BookService books;
  @Mock UserService users;
  @Mock SummaryService summaries;
  MockMvc mvc;
  UserEntity user;
  BookEntity book;

  @BeforeEach
  void setup() {
    mvc =
        MockMvcBuilders.standaloneSetup(new BookController(users, books, summaries))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    user = new UserEntity();
    user.setId(UUID.randomUUID());
    book = new BookEntity();
    book.setId(UUID.randomUUID());
    book.setName("Book");
    book.setCurrencyCode("USD");
    book.setTimezone("UTC");
  }

  void user() {
    when(users.getOrCreate(any(), any(), any())).thenReturn(user);
  }

  @Test
  void listIsAdditive() throws Exception {
    user();
    when(books.listWithBalances(user)).thenReturn(List.of(new BookService.WithBalance(book, 123)));
    mvc.perform(get("/v1/books"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].balanceMinor").value(123))
        .andExpect(jsonPath("$.items[0].icon").value("book"))
        .andExpect(jsonPath("$.items[0].color").value("green"))
        .andExpect(jsonPath("$.items[0].sortOrder").value(0))
        .andExpect(jsonPath("$.items[0].version").value(0));
  }

  @Test
  void legacyCreateDefaults() throws Exception {
    user();
    when(books.create(user, "Book", "USD", "UTC", 0, null, null)).thenReturn(book);
    mvc.perform(
            post("/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Book\",\"currencyCode\":\"USD\",\"timezone\":\"UTC\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("ETag", "\"0\""))
        .andExpect(jsonPath("$.openingBalanceMinor").value(0))
        .andExpect(jsonPath("$.balanceMinor").value(0));
  }

  @Test
  void getPreservesEtag() throws Exception {
    user();
    when(books.requireOwned(book.getId(), user)).thenReturn(book);
    when(summaries.balance(book)).thenReturn(new BalanceResponse(book.getId(), "USD", 50));
    mvc.perform(get("/v1/books/" + book.getId()))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"0\""))
        .andExpect(jsonPath("$.balanceMinor").value(50));
  }

  @Test
  void patchUsesTransactionalVersionCheck() throws Exception {
    user();
    book.setVersion(1L);
    when(books.update(user, book.getId(), 0, "New", null, null)).thenReturn(book);
    when(summaries.balance(book)).thenReturn(new BalanceResponse(book.getId(), "USD", 0));
    mvc.perform(
            patch("/v1/books/" + book.getId())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""));
  }

  @Test
  void patchRejectsEmptyAndBlank() throws Exception {
    for (String body : List.of("{}", "{\"name\":\"   \"}"))
      mvc.perform(
              patch("/v1/books/" + book.getId())
                  .header("If-Match", "0")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(books, users);
  }

  @Test
  void missingAndMalformedIfMatch() throws Exception {
    mvc.perform(delete("/v1/books/" + book.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("MISSING_IF_MATCH"));
    user();
    for (String value : List.of("abc", " "))
      mvc.perform(delete("/v1/books/" + book.getId()).header("If-Match", value))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.error.code")
                  .value(value.isBlank() ? "MISSING_IF_MATCH" : "INVALID_IF_MATCH"));
  }

  @Test
  void staleVersion() throws Exception {
    user();
    doThrow(new ApiException(HttpStatus.PRECONDITION_FAILED, "ETAG_MISMATCH", "Modified"))
        .when(books)
        .delete(user, book.getId(), 0);
    mvc.perform(delete("/v1/books/" + book.getId()).header("If-Match", "0"))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.error.code").value("ETAG_MISMATCH"));
  }

  @Test
  void deleteAndReorderReturn204() throws Exception {
    user();
    mvc.perform(delete("/v1/books/" + book.getId()).header("If-Match", "0"))
        .andExpect(status().isNoContent());
    mvc.perform(
            put("/v1/books/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookIds\":[\"" + book.getId() + "\"]}"))
        .andExpect(status().isNoContent());
    verify(books).delete(user, book.getId(), 0);
    verify(books).reorder(user, List.of(book.getId()));
  }
}
