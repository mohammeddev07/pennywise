package com.axel.pennywise.api.controller;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.axel.pennywise.api.dto.category.CategoryCreateRequest;
import com.axel.pennywise.api.dto.category.CategoryUpdateRequest;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.category.CategoryType;
import com.axel.pennywise.domain.transaction.TransactionRepository;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Collections;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

  private MockMvc mockMvc;

  @Mock private UserService userService;
  @Mock private BookService bookService;
  @Mock private CategoryRepository categoryRepository;
  @Mock private TransactionRepository transactionRepository;

  private ObjectMapper objectMapper;

  private UserEntity testUser;
  private BookEntity testBook;
  private CategoryEntity testCategory;

  private UUID bookId;
  private UUID categoryId;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().findAndRegisterModules();

    CategoryController controller =
        new CategoryController(userService, bookService, categoryRepository, transactionRepository);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TestApiExceptionHandler())
            .build();

    bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    categoryId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    testUser = new UserEntity();
    testUser.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    testBook = new BookEntity();
    testBook.setId(bookId);
    testBook.setName("Test Book");

    testCategory = new CategoryEntity();
    testCategory.setId(categoryId);
    testCategory.setBook(testBook);
    testCategory.setType(CategoryType.EXPENSE);
    testCategory.setName("Food");
    testCategory.setDisabled(false);
    testCategory.setColor("#ABCDEF");
    testCategory.setVersion(0L);
    testCategory.setCreatedAt(OffsetDateTime.now());
    testCategory.setUpdatedAt(OffsetDateTime.now());
  }

  private static RequestPostProcessor auth() {
    return request -> {
      request.setUserPrincipal(new TestingAuthenticationToken("test-user", "N/A"));
      return request;
    };
  }

  @Test
  void testListCategories() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
    when(categoryRepository.findAllByBook_IdAndDeletedAtIsNull(bookId))
        .thenReturn(Collections.singletonList(testCategory));

    mockMvc
        .perform(get("/v1/books/{bookId}/categories", bookId).with(auth()))
        .andExpect(status().isOk());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(categoryRepository).findAllByBook_IdAndDeletedAtIsNull(bookId);
    verifyNoMoreInteractions(userService, bookService, categoryRepository);
  }

  @Test
  void testCreateCategory() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

    // Controller normalizes name; keep stubs aligned with actual call arguments.
    when(categoryRepository.existsByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(
            eq(bookId), eq(CategoryType.INCOME), eq("Salary")))
        .thenReturn(false);

    // Return an entity that matches what you want back
    CategoryEntity saved = new CategoryEntity();
    saved.setId(categoryId);
    saved.setBook(testBook);
    saved.setType(CategoryType.INCOME);
    saved.setName("Salary");
    saved.setDisabled(false);
    saved.setVersion(0L);
    saved.setCreatedAt(OffsetDateTime.now());
    saved.setUpdatedAt(OffsetDateTime.now());

    when(categoryRepository.save(any(CategoryEntity.class))).thenReturn(saved);

    CategoryCreateRequest req = new CategoryCreateRequest(CategoryType.INCOME, "Salary");

    mockMvc
        .perform(
            post("/v1/books/{bookId}/categories", bookId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(categoryRepository)
        .existsByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(
            bookId, CategoryType.INCOME, "Salary");
    verify(categoryRepository).save(any(CategoryEntity.class));
    verifyNoMoreInteractions(userService, bookService, categoryRepository);
  }

  @Test
  void testCreateCategoryWithBlankName() throws Exception {
    // IMPORTANT: do NOT stub anything.
    // With @Valid, this typically fails before controller invocation.
    CategoryCreateRequest req = new CategoryCreateRequest(CategoryType.EXPENSE, "  ");

    mockMvc
        .perform(
            post("/v1/books/{bookId}/categories", bookId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService, bookService, categoryRepository);
  }

  @Test
  void testUpdateCategory() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

    when(categoryRepository.findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId))
        .thenReturn(Optional.of(testCategory));

    // Because "Food" -> "Groceries" triggers the uniqueness check
    when(categoryRepository.existsByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(
            eq(bookId), eq(CategoryType.EXPENSE), eq("Groceries")))
        .thenReturn(false);

    // return the saved entity (optionally bump version so ETag changes)
    when(categoryRepository.save(any(CategoryEntity.class)))
        .thenAnswer(
            inv -> {
              CategoryEntity c = inv.getArgument(0);
              c.setVersion(1L);
              return c;
            });

    CategoryUpdateRequest req = new CategoryUpdateRequest("Groceries", null);

    mockMvc
        .perform(
            patch("/v1/books/{bookId}/categories/{categoryId}", bookId, categoryId)
                .contentType("application/json")
                .header("If-Match", "\"0\"")
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);

    verify(categoryRepository).findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId);
    verify(categoryRepository)
        .existsByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(
            bookId, CategoryType.EXPENSE, "Groceries");
    verify(categoryRepository).save(any(CategoryEntity.class));

    verifyNoMoreInteractions(userService, bookService, categoryRepository);
  }

  @Test
  void testUpdateCategoryAllowsBlankColorToClearIt() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
    when(categoryRepository.findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId))
        .thenReturn(Optional.of(testCategory));
    when(categoryRepository.save(testCategory))
        .thenAnswer(
            inv -> {
              CategoryEntity category = inv.getArgument(0);
              category.setVersion(1L);
              return category;
            });

    CategoryUpdateRequest req = new CategoryUpdateRequest(null, null, null, "");

    mockMvc
        .perform(
            patch("/v1/books/{bookId}/categories/{categoryId}", bookId, categoryId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"0\"")
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.color").doesNotExist());

    assertNull(testCategory.getColor());
    verify(categoryRepository).save(testCategory);
  }

  @Test
  void testUpdateCategoryRejectsEmptyObject() throws Exception {
    mockMvc
        .perform(
            patch("/v1/books/{bookId}/categories/{categoryId}", bookId, categoryId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"0\"")
                .content("{}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService, bookService, categoryRepository, transactionRepository);
  }

  @Test
  void testUpdateCategoryWithoutIfMatch() throws Exception {
    // IMPORTANT: do NOT stub anything.
    // Missing required header fails before controller invocation.
    CategoryUpdateRequest req = new CategoryUpdateRequest("Updated Name", null);

    mockMvc
        .perform(
            patch("/v1/books/{bookId}/categories/{categoryId}", bookId, categoryId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService, bookService, categoryRepository);
  }

  @Test
  void testDeleteCategory() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
    when(categoryRepository.findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId))
        .thenReturn(Optional.of(testCategory));
    when(transactionRepository.existsByBook_IdAndCategory_IdAndDeletedAtIsNull(bookId, categoryId))
        .thenReturn(false);

    mockMvc
        .perform(
            delete("/v1/books/{bookId}/categories/{categoryId}", bookId, categoryId)
                .with(auth())
                .header("If-Match", "\"0\""))
        .andExpect(status().isNoContent());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(categoryRepository).findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId);
    verify(transactionRepository)
        .existsByBook_IdAndCategory_IdAndDeletedAtIsNull(bookId, categoryId);
    verify(categoryRepository).save(testCategory);
    verifyNoMoreInteractions(userService, bookService, categoryRepository, transactionRepository);
  }

  @Test
  void testDeleteCategoryInUse() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
    when(categoryRepository.findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId))
        .thenReturn(Optional.of(testCategory));
    when(transactionRepository.existsByBook_IdAndCategory_IdAndDeletedAtIsNull(bookId, categoryId))
        .thenReturn(true);

    mockMvc
        .perform(
            delete("/v1/books/{bookId}/categories/{categoryId}", bookId, categoryId)
                .with(auth())
                .header("If-Match", "\"0\""))
        .andExpect(status().isConflict());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(categoryRepository).findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId);
    verify(transactionRepository)
        .existsByBook_IdAndCategory_IdAndDeletedAtIsNull(bookId, categoryId);
    verify(categoryRepository, never()).save(any());
    verifyNoMoreInteractions(userService, bookService, categoryRepository, transactionRepository);
  }

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
          // Ignore
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
