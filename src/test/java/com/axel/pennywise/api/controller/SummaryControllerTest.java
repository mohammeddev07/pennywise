package com.axel.pennywise.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.summary.SummaryService;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@ExtendWith(MockitoExtension.class)
class SummaryControllerTest {

  private MockMvc mockMvc;

  @Mock private UserService userService;
  @Mock private BookService bookService;
  @Mock private SummaryService summaryService;

  private UserEntity testUser;
  private BookEntity testBook;
  private UUID bookId;

  @BeforeEach
  void setUp() {

    SummaryController controller = new SummaryController(userService, bookService, summaryService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TestApiExceptionHandler())
            .build();

    bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    testUser = new UserEntity();
    testUser.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    testBook = new BookEntity();
    testBook.setId(bookId);
    testBook.setCurrencyCode("USD");
    testBook.setCreatedAt(OffsetDateTime.now());
    testBook.setUpdatedAt(OffsetDateTime.now());
  }

  private static RequestPostProcessor auth() {
    return request -> {
      request.setUserPrincipal(new TestingAuthenticationToken("test-user", "N/A"));
      return request;
    };
  }

  @Test
  void testGetBalance() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
    when(summaryService.balance(testBook)).thenReturn(null); // keep simple; just want 200

    mockMvc
        .perform(get("/v1/books/{bookId}/balance", bookId).with(auth()))
        .andExpect(status().isOk());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(summaryService).balance(testBook);
    verifyNoMoreInteractions(userService, bookService, summaryService);
  }

  @Test
  void testGetMonthlySummary() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

    YearMonth ym = YearMonth.of(2026, 1);
    when(summaryService.parseMonthOrThrow("2026-01")).thenReturn(ym);
    when(summaryService.monthly(testBook, ym)).thenReturn(null);

    mockMvc
        .perform(
            get("/v1/books/{bookId}/summary/monthly", bookId)
                .with(auth())
                .param("month", "2026-01"))
        .andExpect(status().isOk());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(summaryService).parseMonthOrThrow("2026-01");
    verify(summaryService).monthly(testBook, ym);
    verifyNoMoreInteractions(userService, bookService, summaryService);
  }

  @Test
  void testGetMonthlySummaryWithInvalidMonth() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

    // THIS is what makes the controller return 400.
    when(summaryService.parseMonthOrThrow("not-a-valid-month"))
        .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "Invalid month"));

    mockMvc
        .perform(
            get("/v1/books/{bookId}/summary/monthly", bookId)
                .with(auth())
                .param("month", "not-a-valid-month"))
        .andExpect(status().isBadRequest());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(summaryService).parseMonthOrThrow("not-a-valid-month");
    verifyNoMoreInteractions(userService, bookService, summaryService);
  }

  @Test
  void testGetMonthlySummaryMissingMonth() throws Exception {
    // Missing required @RequestParam => Spring returns 400 before controller runs.
    mockMvc
        .perform(get("/v1/books/{bookId}/summary/monthly", bookId).with(auth()))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService, bookService, summaryService);
  }

  @Test
  void testGetRangeSummary() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

    LocalDate start = LocalDate.of(2026, 1, 5);
    LocalDate end = LocalDate.of(2026, 1, 8);
    when(summaryService.range(testBook, start, end)).thenReturn(null);

    mockMvc
        .perform(
            get("/v1/books/{bookId}/summary/range", bookId)
                .with(auth())
                .param("startDate", "2026-01-05")
                .param("endDate", "2026-01-08"))
        .andExpect(status().isOk());

    verify(userService).getOrCreate(any(), any(), any());
    verify(bookService).requireOwned(bookId, testUser);
    verify(summaryService).validateRangeOrThrow(start, end);
    verify(summaryService).range(testBook, start, end);
    verifyNoMoreInteractions(userService, bookService, summaryService);
  }

  @Test
  void testGetRangeSummaryMissingParams() throws Exception {
    // Missing required @RequestParam => Spring returns 400 before controller runs.
    mockMvc
        .perform(get("/v1/books/{bookId}/summary/range", bookId).with(auth()))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService, bookService, summaryService);
  }

  @Test
  void testGetRangeSummaryInvalidRangeRejected() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

    LocalDate start = LocalDate.of(2026, 2, 1);
    LocalDate end = LocalDate.of(2026, 1, 1);
    doThrow(new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "startDate must be on or before endDate"))
        .when(summaryService)
        .validateRangeOrThrow(start, end);

    mockMvc
        .perform(
            get("/v1/books/{bookId}/summary/range", bookId)
                .with(auth())
                .param("startDate", "2026-02-01")
                .param("endDate", "2026-01-01"))
        .andExpect(status().isBadRequest());

    verify(summaryService).validateRangeOrThrow(start, end);
    verify(summaryService, never()).range(any(), any(), any());
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
          HttpStatus hs = coerce(v);
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
          HttpStatus hs = coerce(v);
          if (hs != null) return hs;
        } catch (Exception ignored) {
          // Ignore and try next
        }
      }
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static HttpStatus coerce(Object v) {
      if (v instanceof HttpStatus hs) return hs;
      if (v instanceof HttpStatusCode hsc) return HttpStatus.valueOf(hsc.value());
      if (v instanceof Integer i) return HttpStatus.valueOf(i);
      return null;
    }
  }
}
