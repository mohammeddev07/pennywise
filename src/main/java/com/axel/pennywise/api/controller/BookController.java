package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.book.BookCreateRequest;
import com.axel.pennywise.api.dto.book.BookOrderRequest;
import com.axel.pennywise.api.dto.book.BookResponse;
import com.axel.pennywise.api.dto.book.BookUpdateRequest;
import com.axel.pennywise.api.dto.common.ItemsResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.summary.SummaryService;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import com.axel.pennywise.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/books")
@RequiredArgsConstructor
public class BookController {

  private final UserService userService;
  private final BookService bookService;
  private final SummaryService summaryService;

  private static final String LOCAL = "local";
  private static final String LOG_USER_RESOLVED = "User resolved: userId={}";

  @GetMapping
  public ResponseEntity<ItemsResponse<BookResponse>> list(Authentication auth) {
    log.info("LIST books");
    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    log.debug(LOG_USER_RESOLVED, user.getId());

    List<BookResponse> items =
        bookService.listWithBalances(user).stream()
            .map(row -> toResponse(row.book(), row.balanceMinor()))
            .toList();
    log.info("Books listed: userId={}, count={}", user.getId(), items.size());
    return ResponseEntity.ok(new ItemsResponse<>(items));
  }

  @PostMapping
  public ResponseEntity<BookResponse> create(
      Authentication auth, @Valid @RequestBody BookCreateRequest req) {
    log.info(
        "CREATE book: name={}, currency={}, timezone={}",
        req.name(),
        req.currencyCode(),
        req.timezone());

    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    log.debug(LOG_USER_RESOLVED, user.getId());

    BookEntity b =
        bookService.create(
            user,
            req.name(),
            req.currencyCode(),
            req.timezone(),
            req.openingBalanceMinor(),
            req.icon(),
            req.color());
    log.info("Book created: bookId={}, userId={}, name={}", b.getId(), user.getId(), b.getName());

    return ResponseEntity.status(201)
        .eTag(etag(b.getVersion()))
        .body(toResponse(b, b.getOpeningBalanceMinor()));
  }

  @GetMapping("/{bookId}")
  public ResponseEntity<BookResponse> get(Authentication auth, @PathVariable UUID bookId) {
    log.info("GET book: bookId={}", bookId);

    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    log.debug(LOG_USER_RESOLVED, user.getId());

    BookEntity b = bookService.requireOwned(bookId, user);
    return ResponseEntity.ok().eTag(etag(b.getVersion())).body(toResponse(b));
  }

  @PatchMapping("/{bookId}")
  public ResponseEntity<BookResponse> patch(
      Authentication auth,
      @PathVariable UUID bookId,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody BookUpdateRequest req) {
    log.info("PATCH book: bookId={}, ifMatch={}", bookId, ifMatch);

    if (req.name() == null && req.icon() == null && req.color() == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "PATCH request must contain at least one field");
    }
    if (req.name() != null && req.name().isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Book name cannot be blank");
    }

    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    log.debug(LOG_USER_RESOLVED, user.getId());

    BookEntity b =
        bookService.update(
            user, bookId, parseEtagVersion(ifMatch), req.name(), req.icon(), req.color());

    return ResponseEntity.ok().eTag(etag(b.getVersion())).body(toResponse(b));
  }

  @DeleteMapping("/{bookId}")
  public ResponseEntity<Void> delete(
      Authentication auth, @PathVariable UUID bookId, @RequestHeader("If-Match") String ifMatch) {
    log.info("DELETE book: bookId={}, ifMatch={}", bookId, ifMatch);

    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    log.debug(LOG_USER_RESOLVED, user.getId());

    bookService.delete(user, bookId, parseEtagVersion(ifMatch));
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/order")
  public ResponseEntity<Void> reorder(
      Authentication auth, @Valid @RequestBody BookOrderRequest req) {
    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    bookService.reorder(user, req.bookIds());
    return ResponseEntity.noContent().build();
  }

  private BookResponse toResponse(BookEntity b) {
    return toResponse(b, summaryService.balance(b).balanceMinor());
  }

  private BookResponse toResponse(BookEntity b, long balanceMinor) {
    return new BookResponse(
        b.getId(),
        b.getName(),
        b.getCurrencyCode(),
        b.getTimezone(),
        b.getOpeningBalanceMinor(),
        b.getCreatedAt(),
        b.getUpdatedAt(),
        b.getDeletedAt(),
        b.getVersion() == null ? 0 : b.getVersion(),
        b.getIcon(),
        b.getColor(),
        b.getSortOrder(),
        balanceMinor);
  }

  private String etag(Long version) {
    long v = (version == null) ? 0L : version;
    return "\"" + v + "\"";
  }

  private long parseEtagVersion(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new com.axel.pennywise.exception.ApiException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "MISSING_IF_MATCH",
          "If-Match header is required");
    }
    String trimmed = ifMatch.trim();
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }
    try {
      return Long.parseLong(trimmed);
    } catch (NumberFormatException e) {
      throw new com.axel.pennywise.exception.ApiException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "INVALID_IF_MATCH",
          "Invalid If-Match value");
    }
  }
}
