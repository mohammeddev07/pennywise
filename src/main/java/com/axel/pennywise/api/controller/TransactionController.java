package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.common.CursorPage;
import com.axel.pennywise.api.dto.transaction.*;
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
import com.axel.pennywise.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import com.axel.pennywise.exception.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

@Slf4j
@RestController
@RequestMapping("/v1/books/{bookId}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final UserService userService;
    private final BookService bookService;
    private final CategoryRepository categoryRepo;
    private final TransactionRepository txRepo;
    private final TransactionService txService;
    private final IdempotencyService idem;

    private static final String LOCAL = "local";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String TRANSACTION_NOT_FOUND = "Transaction not found";

    @GetMapping
    public ResponseEntity<TransactionListResponse> list(
            Authentication auth,
            @PathVariable UUID bookId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        log.info("LIST transactions for bookId={}, filters: from={}, to={}, type={}, categoryId={}, q={}, limit={}, cursor={}",
                bookId, from, to, type, categoryId, q, limit, cursor);

        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        bookService.requireOwned(bookId, user);

        LocalDate fromDate = (from == null || from.isBlank()) ? null : LocalDate.parse(from);
        LocalDate toDate = (to == null || to.isBlank()) ? null : LocalDate.parse(to);

        TransactionType txType = null;
        if (type != null && !type.isBlank()) {
            try {
                txType = TransactionType.valueOf(type.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid transaction type");
            }
        }

        int pageSize = (limit == null) ? 50 : Math.clamp(limit, 1, 200);

        CursorPage<TransactionEntity> page = txService.list(new TransactionListFilter(
                bookId, fromDate, toDate, txType, categoryId, q, pageSize, cursor,
                null, null, null
        ));

        var items = page.items().stream().map(this::toResponse).toList();
        return ResponseEntity.ok(new TransactionListResponse(new CursorPage<>(items, page.nextCursor())));

    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            Authentication auth,
            @PathVariable UUID bookId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionCreateRequest req
    ) {
        log.info("CREATE transaction for bookId={}, categoryId={}, type={}, amount={}, idempotencyKey={}",
                bookId, req.categoryId(), req.type(), req.amountMinor(), idempotencyKey);

        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );

        // Phase 1 idempotency skeleton: if prior exists, return 409 until Phase 2 replay is implemented.
        String pseudoRequestBody = req.toString();
        var prior = idem.tryGetPrior(user.getId(), idempotencyKey, pseudoRequestBody);
        if (prior.isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_REPLAY_NOT_IMPLEMENTED",
                    "Duplicate request detected for Idempotency-Key; response replay will be implemented in Phase 2",
                    List.of(Map.of("idempotencyKey", idempotencyKey))
            );
        }

        BookEntity book = bookService.requireOwned(bookId, user);

        CategoryEntity category = categoryRepo.findByIdAndBook_IdAndDeletedAtIsNull(req.categoryId(), bookId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, "Category not found"));

        TransactionEntity tx = txService.create(book, category, req.type(), req.amountMinor(), req.occurredOn(), req.note());

        TransactionResponse body = toResponse(tx);

        idem.storeResponse(user.getId(), idempotencyKey, pseudoRequestBody, 201, body.toString());

        return ResponseEntity.status(201).eTag(etag(tx.getVersion())).body(body);
    }

    @GetMapping("/{txId}")
    public ResponseEntity<TransactionResponse> get(
            Authentication auth,
            @PathVariable UUID bookId,
            @PathVariable UUID txId
    ) {
        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        bookService.requireOwned(bookId, user);

        TransactionEntity tx = txRepo.findByIdAndBook_IdAndDeletedAtIsNull(txId, bookId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, TRANSACTION_NOT_FOUND));

        return ResponseEntity.ok().eTag(etag(tx.getVersion())).body(toResponse(tx));
    }

    @PatchMapping("/{txId}")
    public ResponseEntity<TransactionResponse> patch(
            Authentication auth,
            @PathVariable UUID bookId,
            @PathVariable UUID txId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody TransactionUpdateRequest req
    ) {
        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        bookService.requireOwned(bookId, user);

        TransactionEntity tx = txRepo.findByIdAndBook_IdAndDeletedAtIsNull(txId, bookId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, TRANSACTION_NOT_FOUND));

        requireIfMatch(tx.getVersion(), ifMatch);

        // Apply updates
        TransactionEntity updated = txService.update(tx, req);

        return ResponseEntity.ok().eTag(etag(updated.getVersion())).body(toResponse(updated));
    }

    @DeleteMapping("/{txId}")
    public ResponseEntity<Void> delete(
            Authentication auth,
            @PathVariable UUID bookId,
            @PathVariable UUID txId,
            @RequestHeader("If-Match") String ifMatch
    ) {
        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        bookService.requireOwned(bookId, user);

        TransactionEntity tx = txRepo.findByIdAndBook_IdAndDeletedAtIsNull(txId, bookId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, TRANSACTION_NOT_FOUND));

        requireIfMatch(tx.getVersion(), ifMatch);

        txService.softDelete(tx);

        return ResponseEntity.noContent().build();
    }

    private TransactionResponse toResponse(TransactionEntity tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getBook().getId(),
                tx.getType(),
                tx.getAmountMinor(),
                tx.getOccurredOn(),
                tx.getCategory().getId(),
                tx.getNote(),
                tx.getCreatedAt(),
                tx.getUpdatedAt(),
                tx.getDeletedAt(),
                tx.getVersion() == null ? 0 : tx.getVersion()
        );
    }

    private String etag(Long version) {
        long v = (version == null) ? 0L : version;
        return "\"" + v + "\"";
    }

    private void requireIfMatch(Long currentVersion, String ifMatch) {
        long expected = parseEtagVersion(ifMatch);
        long actual = (currentVersion == null) ? 0L : currentVersion;
        if (expected != actual) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_FAILED,
                    "ETAG_MISMATCH",
                    "Resource was modified. Re-fetch and retry.",
                    List.of(Map.of("expected", String.valueOf(expected), "actual", String.valueOf(actual)))
            );
        }
    }

    private long parseEtagVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_IF_MATCH", "If-Match header is required");
        }
        String trimmed = ifMatch.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IF_MATCH", "Invalid If-Match value");
        }
    }
}

