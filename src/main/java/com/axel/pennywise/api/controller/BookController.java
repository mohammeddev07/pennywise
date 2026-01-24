package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.book.BookCreateRequest;
import com.axel.pennywise.api.dto.book.BookResponse;
import com.axel.pennywise.api.dto.book.BookUpdateRequest;
import com.axel.pennywise.api.dto.common.ItemsResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/books")
@RequiredArgsConstructor
public class BookController {

    private final UserService userService;
    private final BookService bookService;

    private static final String LOCAL = "local";
    private static final String LOG_USER_RESOLVED = "User resolved: userId={}";

    @GetMapping
    public ResponseEntity<ItemsResponse<BookResponse>> list(Authentication auth) {
        log.info("LIST books");
        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        log.debug(LOG_USER_RESOLVED, user.getId());

        List<BookResponse> items = bookService.list(user).stream().map(this::toResponse).toList();
        log.info("Books listed: userId={}, count={}", user.getId(), items.size());
        return ResponseEntity.ok(new ItemsResponse<>(items));
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(Authentication auth, @Valid @RequestBody BookCreateRequest req) {
        log.info("CREATE book: name={}, currency={}, timezone={}", req.name(), req.currencyCode(), req.timezone());

        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        log.debug(LOG_USER_RESOLVED, user.getId());

        BookEntity b = bookService.create(user, req.name(), req.currencyCode(), req.timezone(), req.openingBalanceMinor());
        log.info("Book created: bookId={}, userId={}, name={}", b.getId(), user.getId(), b.getName());

        return ResponseEntity.status(201)
                .eTag(etag(b.getVersion()))
                .body(toResponse(b));
    }

    @GetMapping("/{bookId}")
    public ResponseEntity<BookResponse> get(Authentication auth, @PathVariable UUID bookId) {
        log.info("GET book: bookId={}", bookId);

        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        log.debug(LOG_USER_RESOLVED, user.getId());

        BookEntity b = bookService.requireOwned(bookId, user);
        return ResponseEntity.ok().eTag(etag(b.getVersion())).body(toResponse(b));
    }

    @PatchMapping("/{bookId}")
    public ResponseEntity<BookResponse> patch(
            Authentication auth,
            @PathVariable UUID bookId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody BookUpdateRequest req
    ) {
        log.info("PATCH book: bookId={}, ifMatch={}", bookId, ifMatch);

        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        log.debug(LOG_USER_RESOLVED, user.getId());

        BookEntity b = bookService.requireOwned(bookId, user);

        // Enforce optimistic concurrency
        requireIfMatch(b, ifMatch);

        // Apply changes (name only)
        if (req.name() != null && !req.name().isBlank()) {
            b = bookService.updateName(b, req.name().trim()); // persist + increment version
        }

        return ResponseEntity.ok().eTag(etag(b.getVersion())).body(toResponse(b));
    }

    private BookResponse toResponse(BookEntity b) {
        return new BookResponse(
                b.getId(),
                b.getName(),
                b.getCurrencyCode(),
                b.getTimezone(),
                b.getOpeningBalanceMinor(),
                b.getCreatedAt(),
                b.getUpdatedAt(),
                b.getDeletedAt(),
                b.getVersion() == null ? 0 : b.getVersion()
        );
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
                    "If-Match header is required"
            );
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
                    "Invalid If-Match value"
            );
        }
    }

    private void requireIfMatch(BookEntity b, String ifMatch) {
        long expected = parseEtagVersion(ifMatch);
        long actual = (b.getVersion() == null) ? 0L : b.getVersion();
        if (expected != actual) {
            throw new com.axel.pennywise.exception.ApiException(
                    org.springframework.http.HttpStatus.PRECONDITION_FAILED,
                    "ETAG_MISMATCH",
                    "Resource was modified. Re-fetch and retry.",
                    List.of(Map.of("expected", String.valueOf(expected), "actual", String.valueOf(actual)))
            );
        }
    }
}
