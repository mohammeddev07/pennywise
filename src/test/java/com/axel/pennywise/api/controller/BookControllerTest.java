package com.axel.pennywise.api.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.axel.pennywise.api.dto.book.BookCreateRequest;
import com.axel.pennywise.api.dto.book.BookUpdateRequest;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.transaction.TransactionRepository;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@ExtendWith(MockitoExtension.class)
class BookControllerTest {

  private MockMvc mockMvc;

  @Mock private BookService bookService;
  @Mock private UserService userService;
  @Mock private TransactionRepository transactionRepository;

  private ObjectMapper objectMapper;

  private UserEntity testUser;
  private UUID bookId;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().findAndRegisterModules();

    BookController controller = new BookController(userService, bookService, transactionRepository);

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TestApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    bookId = UUID.fromString("58484688-12fb-4730-a239-5a31366e9227");

    testUser = new UserEntity();
    testUser.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    testUser.setAuthSubject("test");
  }

  private static RequestPostProcessor auth() {
    return request -> {
      request.setUserPrincipal(new TestingAuthenticationToken("test-user", "N/A"));
      return request;
    };
  }

  private static BookEntity book(UUID id, String name, long version) {
    BookEntity b = new BookEntity();
    b.setId(id);
    b.setName(name);
    b.setCurrencyCode("USD");
    b.setTimezone("UTC");
    b.setOpeningBalanceMinor(0L);
    b.setVersion(version);
    b.setCreatedAt(OffsetDateTime.now());
    b.setUpdatedAt(OffsetDateTime.now());
    return b;
  }

  @Test
  void testListBooks() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);

    BookEntity testBook = book(bookId, "Test Book", 0L);
    when(bookService.list(testUser)).thenReturn(List.of(testBook));

    mockMvc
        .perform(get("/v1/books").with(auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(bookId.toString()))
        .andExpect(jsonPath("$.items[0].version").value(0));

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).list(testUser);
    verifyNoMoreInteractions(bookService, userService);
  }

  @Test
  void testCreateBook() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);

    UUID createdId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    BookEntity created = new BookEntity();
    created.setId(createdId);
    created.setName("New Book");
    created.setCurrencyCode("EUR");
    created.setTimezone("Europe/London");
    created.setOpeningBalanceMinor(5000L);
    created.setVersion(0L);
    created.setCreatedAt(OffsetDateTime.now());
    created.setUpdatedAt(OffsetDateTime.now());

    when(bookService.create(
            eq(testUser), eq("New Book"), eq("EUR"), eq("Europe/London"), eq(5000L)))
        .thenReturn(created);

    BookCreateRequest req = new BookCreateRequest("New Book", "EUR", "Europe/London", 5000L);

    mockMvc
        .perform(
            post("/v1/books")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(header().string("ETag", "\"0\""))
        .andExpect(jsonPath("$.name").value("New Book"))
        .andExpect(jsonPath("$.currencyCode").value("EUR"))
        .andExpect(jsonPath("$.timezone").value("Europe/London"))
        .andExpect(jsonPath("$.openingBalanceMinor").value(5000));

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).create(testUser, "New Book", "EUR", "Europe/London", 5000L);
    verifyNoMoreInteractions(bookService, userService);
  }

  @Test
  void testGetBook() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);

    BookEntity testBook = book(bookId, "Test Book", 0L);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

    mockMvc
        .perform(get("/v1/books/{bookId}", bookId).with(auth()))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"0\""))
        .andExpect(jsonPath("$.id").value(bookId.toString()))
        .andExpect(jsonPath("$.name").value("Test Book"));

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verifyNoMoreInteractions(bookService, userService);
  }

  @Test
  void testUpdateBook() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);

    BookEntity existing = book(bookId, "Test Book", 0L);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(existing);

    BookEntity updated = book(bookId, "Updated Book", 1L);
    when(bookService.updateName(existing, "Updated Book")).thenReturn(updated);

    BookUpdateRequest req = new BookUpdateRequest("Updated Book");

    mockMvc
        .perform(
            patch("/v1/books/{bookId}", bookId)
                .with(auth())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""))
        .andExpect(jsonPath("$.name").value("Updated Book"))
        .andExpect(jsonPath("$.version").value(1));

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(bookService).updateName(existing, "Updated Book");
    verifyNoMoreInteractions(bookService, userService);
  }

  @Test
  void testUpdateBookRejectsEmptyObject() throws Exception {
    mockMvc
        .perform(
            patch("/v1/books/{bookId}", bookId)
                .with(auth())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService, bookService, transactionRepository);
  }

  @Test
  void testUpdateBookRejectsBlankName() throws Exception {
    mockMvc
        .perform(
            patch("/v1/books/{bookId}", bookId)
                .with(auth())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService, bookService, transactionRepository);
  }

  @Test
  void testUpdateBookWithoutIfMatch() throws Exception {
    // No stubbing here on purpose: controller method is NOT invoked when header is missing.
    BookUpdateRequest req = new BookUpdateRequest("Another Name");

    mockMvc
        .perform(
            patch("/v1/books/{bookId}", bookId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService, bookService);
  }

  @Test
  void testUpdateBookWithWrongEtag() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);

    BookEntity version1 = book(bookId, "Test Book", 1L);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(version1);

    BookUpdateRequest req = new BookUpdateRequest("Another Name");

    mockMvc
        .perform(
            patch("/v1/books/{bookId}", bookId)
                .with(auth())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isPreconditionFailed());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(bookService, never()).updateName(any(), anyString());
    verifyNoMoreInteractions(bookService, userService);
  }

  @Test
  void testUpdateBookWithInvalidEtagFormat() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);

    BookEntity version0 = book(bookId, "Test Book", 0L);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(version0);

    BookUpdateRequest req = new BookUpdateRequest("Another Name");

    mockMvc
        .perform(
            patch("/v1/books/{bookId}", bookId)
                .with(auth())
                .header("If-Match", "\"abc\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verifyNoMoreInteractions(bookService, userService);
  }

  @Test
  void testDeleteBook() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);

    BookEntity existing = book(bookId, "Test Book", 0L);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(existing);
    when(transactionRepository.existsByBook_IdAndDeletedAtIsNull(bookId)).thenReturn(false);

    mockMvc
        .perform(delete("/v1/books/{bookId}", bookId).with(auth()).header("If-Match", "\"0\""))
        .andExpect(status().isNoContent());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(transactionRepository).existsByBook_IdAndDeletedAtIsNull(bookId);
    verify(bookService).softDelete(existing);
    verifyNoMoreInteractions(bookService, userService, transactionRepository);
  }

  @Test
  void testDeleteBookWithWrongEtag() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);

    BookEntity existing = book(bookId, "Test Book", 1L);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(existing);

    mockMvc
        .perform(delete("/v1/books/{bookId}", bookId).with(auth()).header("If-Match", "\"0\""))
        .andExpect(status().isPreconditionFailed());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verifyNoInteractions(transactionRepository);
    verify(bookService, never()).softDelete(any());
    verifyNoMoreInteractions(bookService, userService);
  }

  @Test
  void testDeleteBookWithTransactions() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);

    BookEntity existing = book(bookId, "Test Book", 0L);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(existing);
    when(transactionRepository.existsByBook_IdAndDeletedAtIsNull(bookId)).thenReturn(true);

    mockMvc
        .perform(delete("/v1/books/{bookId}", bookId).with(auth()).header("If-Match", "\"0\""))
        .andExpect(status().isConflict());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(transactionRepository).existsByBook_IdAndDeletedAtIsNull(bookId);
    verify(bookService, never()).softDelete(any());
    verifyNoMoreInteractions(bookService, userService, transactionRepository);
  }

  /**
   * Minimal test-only exception handler so MockMvc can assert 4xx/412 instead of failing the test
   * with ServletException when ApiException is thrown.
   */
  @RestControllerAdvice
  static class TestApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Void> handle(ApiException ex) {
      return ResponseEntity.status(extractStatus(ex)).build();
    }

    private static HttpStatus extractStatus(ApiException ex) {
      // Try common accessor method names first
      for (String m : List.of("getStatus", "status", "getHttpStatus", "httpStatus")) {
        try {
          Method method = ex.getClass().getMethod(m);
          Object v = method.invoke(ex);
          HttpStatus resolved = coerceToHttpStatus(v);
          if (resolved != null) return resolved;
        } catch (Exception ignored) { // Ignore
        }
      }

      // Try common field names
      for (String f : List.of("status", "httpStatus")) {
        try {
          Field field = ex.getClass().getDeclaredField(f);
          field.setAccessible(true);
          Object v = field.get(ex);
          HttpStatus resolved = coerceToHttpStatus(v);
          if (resolved != null) return resolved;
        } catch (Exception ignored) {
          // Ignore
        }
      }

      // Fallback
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static HttpStatus coerceToHttpStatus(Object v) {
      if (v instanceof HttpStatus hs) return hs;
      if (v instanceof HttpStatusCode hsc) return HttpStatus.valueOf(hsc.value());
      if (v instanceof Integer i) return HttpStatus.valueOf(i);
      return null;
    }
  }
}
