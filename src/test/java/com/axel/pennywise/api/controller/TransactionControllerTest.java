package com.axel.pennywise.api.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.axel.pennywise.api.dto.common.CursorPage;
import com.axel.pennywise.api.dto.transaction.TransactionCreateRequest;
import com.axel.pennywise.api.dto.transaction.TransactionUpdateRequest;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.idempotency.IdempotencyService;
import com.axel.pennywise.domain.transaction.TransactionEntity;
import com.axel.pennywise.domain.transaction.TransactionRepository;
import com.axel.pennywise.domain.transaction.TransactionService;
import com.axel.pennywise.domain.transaction.TransactionType;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
class TransactionControllerTest {

  private MockMvc mockMvc;

  @Mock private UserService userService;
  @Mock private BookService bookService;
  @Mock private CategoryRepository categoryRepository;
  @Mock private TransactionRepository transactionRepository;
  @Mock private TransactionService transactionService;
  @Mock private IdempotencyService idempotencyService;
  @Mock private com.axel.pennywise.domain.transaction.TransactionImportService importService;
  @Mock private com.axel.pennywise.domain.transaction.TransactionExportService exportService;

  private ObjectMapper objectMapper;

  private UserEntity testUser;
  private BookEntity testBook;
  private CategoryEntity testCategory;

  private UUID bookId;
  private UUID categoryId;
  private UUID transactionId;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().findAndRegisterModules();

    TransactionController controller =
        new TransactionController(
            userService,
            bookService,
            categoryRepository,
            transactionRepository,
            transactionService,
            idempotencyService,
            objectMapper,
            importService,
            exportService);

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TestApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    categoryId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    transactionId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    testUser = new UserEntity();
    testUser.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    testBook = new BookEntity();
    testBook.setId(bookId);
    testBook.setCreatedAt(OffsetDateTime.now());
    testBook.setUpdatedAt(OffsetDateTime.now());

    testCategory = new CategoryEntity();
    testCategory.setId(categoryId);
    testCategory.setBook(testBook);
  }

  private static RequestPostProcessor auth() {
    return request -> {
      request.setUserPrincipal(new TestingAuthenticationToken("test-user", "N/A"));
      return request;
    };
  }

  private TransactionEntity tx(UUID id, long version) {
    TransactionEntity t = new TransactionEntity();
    t.setId(id);
    t.setBook(testBook);
    t.setCategory(testCategory); // IMPORTANT: toResponse() requires category
    t.setType(TransactionType.EXPENSE);
    t.setAmountMinor(3000L);
    t.setOccurredOn(LocalDate.of(2026, 1, 1));
    t.setNote("note");
    t.setCreatedAt(OffsetDateTime.now());
    t.setUpdatedAt(OffsetDateTime.now());
    t.setVersion(version);
    return t;
  }

  @Test
  void testListTransactions() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
    when(transactionService.list(any())).thenReturn(new CursorPage<>(List.of(), null));

    mockMvc
        .perform(get("/v1/books/{bookId}/transactions", bookId).with(auth()))
        .andExpect(status().isOk());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(transactionService).list(any());
    verifyNoMoreInteractions(
        userService,
        bookService,
        transactionService,
        categoryRepository,
        transactionRepository,
        idempotencyService);
  }

  @Test
  void testListTransactionsWithFilters() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
    when(transactionService.list(any())).thenReturn(new CursorPage<>(List.of(), null));

    LocalDate today = LocalDate.of(2026, 1, 1);

    mockMvc
        .perform(
            get("/v1/books/{bookId}/transactions", bookId)
                .with(auth())
                .param("from", today.toString())
                .param("to", today.toString())
                .param("type", "EXPENSE"))
        .andExpect(status().isOk());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(transactionService).list(any());
    verifyNoMoreInteractions(
        userService,
        bookService,
        transactionService,
        categoryRepository,
        transactionRepository,
        idempotencyService);
  }

  @Test
  void testCreateTransaction() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(idempotencyService.tryGetPrior(eq(testUser.getId()), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
    when(categoryRepository.findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId))
        .thenReturn(Optional.of(testCategory));

    TransactionEntity created = tx(transactionId, 0L);
    when(transactionService.create(
            eq(testBook),
            eq(testCategory),
            eq(TransactionType.EXPENSE),
            eq(3000L),
            any(),
            eq("Lunch"),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(created);
    when(transactionRepository.findByIdAndBook_IdAndDeletedAtIsNull(transactionId, bookId))
        .thenReturn(Optional.of(created));

    TransactionCreateRequest req =
        new TransactionCreateRequest(
            TransactionType.EXPENSE, 3000L, LocalDate.of(2026, 1, 1), categoryId, "Lunch");

    mockMvc
        .perform(
            post("/v1/books/{bookId}/transactions", bookId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-key-1")
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated());

    verify(userService).getOrCreate(any(), any(), any());
    verify(idempotencyService).tryGetPrior(eq(testUser.getId()), eq("idem-key-1"), anyString());
    verify(bookService).requireOwned(bookId, testUser);
    verify(categoryRepository).findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId);
    verify(transactionService)
        .create(
            eq(testBook),
            eq(testCategory),
            eq(TransactionType.EXPENSE),
            eq(3000L),
            eq(LocalDate.of(2026, 1, 1)),
            eq("Lunch"),
            isNull(),
            isNull(),
            isNull());
    verify(transactionRepository).findByIdAndBook_IdAndDeletedAtIsNull(transactionId, bookId);
    verify(idempotencyService)
        .storeResponse(eq(testUser.getId()), eq("idem-key-1"), anyString(), eq(201), anyString());

    verifyNoMoreInteractions(
        userService,
        bookService,
        categoryRepository,
        transactionRepository,
        transactionService,
        idempotencyService);
  }

  @Test
  void testCreateTransactionWithoutIdempotencyKey() throws Exception {
    // Missing required header => 400 before controller method is invoked
    TransactionCreateRequest req =
        new TransactionCreateRequest(
            TransactionType.EXPENSE, 3000L, LocalDate.of(2026, 1, 1), categoryId, "Lunch");

    mockMvc
        .perform(
            post("/v1/books/{bookId}/transactions", bookId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(
        userService,
        bookService,
        categoryRepository,
        transactionRepository,
        transactionService,
        idempotencyService);
  }

  @Test
  void testGetTransaction() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

    TransactionEntity found = tx(transactionId, 0L);
    when(transactionRepository.findByIdAndBook_IdAndDeletedAtIsNull(transactionId, bookId))
        .thenReturn(Optional.of(found));

    mockMvc
        .perform(get("/v1/books/{bookId}/transactions/{txId}", bookId, transactionId).with(auth()))
        .andExpect(status().isOk());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(transactionRepository).findByIdAndBook_IdAndDeletedAtIsNull(transactionId, bookId);
    verifyNoMoreInteractions(
        userService,
        bookService,
        categoryRepository,
        transactionRepository,
        transactionService,
        idempotencyService);
  }

  @Test
  void testUpdateTransaction() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

    TransactionEntity existing = tx(transactionId, 0L);
    when(transactionRepository.findByIdAndBook_IdAndDeletedAtIsNull(transactionId, bookId))
        .thenReturn(Optional.of(existing));

    TransactionEntity updated = tx(transactionId, 1L);
    updated.setNote("Updated note");
    when(transactionService.update(eq(existing), any())).thenReturn(updated);
    when(transactionRepository.findByIdAndBook_IdAndDeletedAtIsNull(transactionId, bookId))
        .thenReturn(Optional.of(existing), Optional.of(updated));

    TransactionUpdateRequest req =
        new TransactionUpdateRequest(null, null, null, null, "Updated note");

    mockMvc
        .perform(
            patch("/v1/books/{bookId}/transactions/{txId}", bookId, transactionId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"0\"")
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(transactionRepository, times(2))
        .findByIdAndBook_IdAndDeletedAtIsNull(transactionId, bookId);
    verify(transactionService).update(eq(existing), any());
    verifyNoMoreInteractions(
        userService,
        bookService,
        categoryRepository,
        transactionRepository,
        transactionService,
        idempotencyService);
  }

  @Test
  void testUpdateTransactionRejectsEmptyObject() throws Exception {
    mockMvc
        .perform(
            patch("/v1/books/{bookId}/transactions/{txId}", bookId, transactionId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"0\"")
                .content("{}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(
        userService,
        bookService,
        categoryRepository,
        transactionRepository,
        transactionService,
        idempotencyService);
  }

  @Test
  void testUpdateTransactionWithoutIfMatch() throws Exception {
    // Missing required header => 400 before controller method is invoked
    TransactionUpdateRequest req =
        new TransactionUpdateRequest(null, null, null, null, "Updated note");

    mockMvc
        .perform(
            patch("/v1/books/{bookId}/transactions/{txId}", bookId, transactionId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(
        userService,
        bookService,
        categoryRepository,
        transactionRepository,
        transactionService,
        idempotencyService);
  }

  @Test
  void testDeleteTransaction() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

    TransactionEntity existing = tx(transactionId, 0L);
    when(transactionRepository.findByIdAndBook_IdAndDeletedAtIsNull(transactionId, bookId))
        .thenReturn(Optional.of(existing));

    mockMvc
        .perform(
            delete("/v1/books/{bookId}/transactions/{txId}", bookId, transactionId)
                .with(auth())
                .header("If-Match", "\"0\""))
        .andExpect(status().isNoContent());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(transactionRepository).findByIdAndBook_IdAndDeletedAtIsNull(transactionId, bookId);
    verify(transactionService).softDelete(existing);
    verifyNoMoreInteractions(
        userService,
        bookService,
        categoryRepository,
        transactionRepository,
        transactionService,
        idempotencyService);
  }

  @Test
  void testDeleteTransactionWithoutIfMatch() throws Exception {
    // Missing required header => 400 before controller method is invoked
    mockMvc
        .perform(
            delete("/v1/books/{bookId}/transactions/{txId}", bookId, transactionId).with(auth()))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(
        userService,
        bookService,
        categoryRepository,
        transactionRepository,
        transactionService,
        idempotencyService);
  }

  /**
   * Test-only exception mapping so ApiException becomes an HTTP status in MockMvc standalone tests.
   */
  @RestControllerAdvice
  static class TestApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Void> handle(ApiException ex) {
      return ResponseEntity.status(extractStatus(ex)).build();
    }

    private static HttpStatus extractStatus(ApiException ex) {
      for (String m : List.of("getStatus", "status", "getHttpStatus", "httpStatus")) {
        try {
          Method method = ex.getClass().getMethod(m);
          Object v = method.invoke(ex);
          HttpStatus hs = coerceToHttpStatus(v);
          if (hs != null) return hs;
        } catch (Exception ignored) {
          // Ignore and try next
        }
      }
      for (String f : List.of("status", "httpStatus")) {
        try {
          Field field = ex.getClass().getDeclaredField(f);
          field.setAccessible(true);
          Object v = field.get(ex);
          HttpStatus hs = coerceToHttpStatus(v);
          if (hs != null) return hs;
        } catch (Exception ignored) {
          // Ignore and try next
        }
      }
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
