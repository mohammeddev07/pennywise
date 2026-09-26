package com.axel.pennywise.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axel.pennywise.api.dto.ai.FilterProposalResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.transaction.query.ai.FilterProposalService;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import com.axel.pennywise.exception.RateLimitExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
class AiFilterControllerTest {

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @Mock private UserService userService;
  @Mock private BookService bookService;
  @Mock private FilterProposalService filterProposalService;

  private UserEntity testUser;
  private BookEntity testBook;
  private UUID bookId;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().findAndRegisterModules();
    AiFilterController controller =
        new AiFilterController(userService, bookService, filterProposalService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TestExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    bookId = UUID.randomUUID();
    testUser = new UserEntity();
    testUser.setId(UUID.randomUUID());
    testBook = new BookEntity();
    testBook.setId(bookId);
    testBook.setCurrencyCode("USD");
    testBook.setTimezone("UTC");
  }

  private static RequestPostProcessor auth() {
    return request -> {
      request.setUserPrincipal(new TestingAuthenticationToken("test-user", "N/A"));
      return request;
    };
  }

  @Test
  void returnsProposalOnSuccess() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
    FilterProposalResponse response =
        new FilterProposalResponse(
            "PROPOSAL",
            Map.of("kind", "group", "op", "AND", "children", List.of()),
            List.of(),
            "all transactions",
            null,
            null);
    when(filterProposalService.propose(eq(testBook), eq(testUser), eq("groceries last month")))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/v1/books/{bookId}/filter-proposals", bookId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"groceries last month\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PROPOSAL"))
        .andExpect(jsonPath("$.summary").value("all transactions"));
  }

  @Test
  void blankTextIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/v1/books/{bookId}/filter-proposals", bookId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(filterProposalService);
  }

  @Test
  void textOverFiveHundredCharsIsRejected() throws Exception {
    String tooLong = "a".repeat(501);
    mockMvc
        .perform(
            post("/v1/books/{bookId}/filter-proposals", bookId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", tooLong))))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(filterProposalService);
  }

  @Test
  void unownedOrUnknownBookIsNotFound() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser))
        .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Book not found"));

    mockMvc
        .perform(
            post("/v1/books/{bookId}/filter-proposals", bookId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"anything\"}"))
        .andExpect(status().isNotFound());

    verifyNoInteractions(filterProposalService);
  }

  @Test
  void rateLimitReturns429WithRetryAfterHeader() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
    when(filterProposalService.propose(any(), any(), any()))
        .thenThrow(
            new RateLimitExceededException(
                "AI_FILTER_RATE_LIMITED", "Too many AI filter requests.", 60));

    mockMvc
        .perform(
            post("/v1/books/{bookId}/filter-proposals", bookId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"anything\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"));
  }

  @Test
  void disabledFeatureReturns503() throws Exception {
    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
    when(filterProposalService.propose(any(), any(), any()))
        .thenThrow(
            new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_FILTERS_DISABLED",
                "AI filters are not enabled"));

    mockMvc
        .perform(
            post("/v1/books/{bookId}/filter-proposals", bookId)
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"anything\"}"))
        .andExpect(status().isServiceUnavailable());
  }

  @RestControllerAdvice
  static class TestExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<Void> handleRateLimit(RateLimitExceededException ex) {
      return ResponseEntity.status(ex.status())
          .header("Retry-After", String.valueOf(ex.retryAfterSeconds()))
          .build();
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Void> handleApi(ApiException ex) {
      return ResponseEntity.status(ex.status()).build();
    }
  }
}
