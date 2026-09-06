package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.common.CursorPage;
import com.axel.pennywise.api.dto.transaction.*;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.idempotency.IdempotencyService;
import com.axel.pennywise.domain.transaction.TransactionEntity;
import com.axel.pennywise.domain.transaction.TransactionExportService;
import com.axel.pennywise.domain.transaction.TransactionImportService;
import com.axel.pennywise.domain.transaction.TransactionRepository;
import com.axel.pennywise.domain.transaction.TransactionService;
import com.axel.pennywise.domain.transaction.TransactionType;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import com.axel.pennywise.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
  private final ObjectMapper objectMapper;
  private final TransactionImportService importService;
  private final TransactionExportService exportService;

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
      @RequestParam(required = false) String cursor) {
    log.info(
        "LIST transactions for bookId={}, filters: from={}, to={}, type={}, categoryId={}, q={},"
            + " limit={}, cursor={}",
        bookId,
        from,
        to,
        type,
        categoryId,
        q,
        limit,
        cursor);

    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    bookService.requireOwned(bookId, user);

    LocalDate fromDate = (from == null || from.isBlank()) ? null : LocalDate.parse(from);
    LocalDate toDate = (to == null || to.isBlank()) ? null : LocalDate.parse(to);

    TransactionType txType = null;
    if (type != null && !type.isBlank()) {
      try {
        txType = TransactionType.valueOf(type.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid transaction type");
      }
    }

    int pageSize = (limit == null) ? 50 : Math.clamp(limit, 1, 200);

    CursorPage<TransactionEntity> page =
        txService.list(
            new TransactionListFilter(
                bookId,
                fromDate,
                toDate,
                txType,
                categoryId,
                q,
                pageSize,
                cursor,
                null,
                null,
                null));

    var items = page.items().stream().map(this::toResponse).toList();
    return ResponseEntity.ok(
        new TransactionListResponse(new CursorPage<>(items, page.nextCursor())));
  }

  @PostMapping
  public ResponseEntity<TransactionResponse> create(
      Authentication auth,
      @PathVariable UUID bookId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody TransactionCreateRequest req) {
    log.info(
        "CREATE transaction for bookId={}, categoryId={}, type={}, amount={}, idempotencyKey={}",
        bookId,
        req.categoryId(),
        req.type(),
        req.amountMinor(),
        idempotencyKey);

    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));

    String requestBodyJson = writeJson(req);
    var prior = idem.tryGetPrior(user.getId(), idempotencyKey, requestBodyJson);
    if (prior.isPresent()) {
      IdempotencyService.PriorResponse replay = prior.get();
      if (replay.statusCode() == 409) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_KEY_REUSED",
            "Idempotency-Key reused with a different request",
            List.of(Map.of("idempotencyKey", idempotencyKey)));
      }
      TransactionResponse replayed = readTransactionResponse(replay.body());
      return ResponseEntity.status(replay.statusCode())
          .eTag(etag(replayed.version()))
          .body(replayed);
    }

    BookEntity book = bookService.requireOwned(bookId, user);

    CategoryEntity category =
        categoryRepo
            .findByIdAndBook_IdAndDeletedAtIsNull(req.categoryId(), bookId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, "Category not found"));

    TransactionEntity tx =
        txService.create(
            book,
            category,
            req.type(),
            req.amountMinor(),
            req.occurredOn(),
            req.note(),
            req.title(),
            req.paymentMethod(),
            req.occurredAt());

    TransactionEntity initializedTx = reloadTransaction(bookId, tx.getId());
    TransactionResponse body = toResponse(initializedTx);

    idem.storeResponse(user.getId(), idempotencyKey, requestBodyJson, 201, writeJson(body));

    return ResponseEntity.status(201).eTag(etag(initializedTx.getVersion())).body(body);
  }

  @GetMapping("/{txId}")
  public ResponseEntity<TransactionResponse> get(
      Authentication auth, @PathVariable UUID bookId, @PathVariable UUID txId) {
    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    bookService.requireOwned(bookId, user);

    TransactionEntity tx =
        txRepo
            .findByIdAndBook_IdAndDeletedAtIsNull(txId, bookId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, TRANSACTION_NOT_FOUND));

    return ResponseEntity.ok().eTag(etag(tx.getVersion())).body(toResponse(tx));
  }

  @PatchMapping("/{txId}")
  public ResponseEntity<TransactionResponse> patch(
      Authentication auth,
      @PathVariable UUID bookId,
      @PathVariable UUID txId,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody TransactionUpdateRequest req) {
    if (req.type() == null
        && req.amountMinor() == null
        && req.occurredOn() == null
        && req.categoryId() == null
        && req.note() == null
        && req.title() == null
        && req.paymentMethod() == null
        && req.occurredAt() == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "PATCH request must contain at least one field");
    }

    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    bookService.requireOwned(bookId, user);

    TransactionEntity tx =
        txRepo
            .findByIdAndBook_IdAndDeletedAtIsNull(txId, bookId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, TRANSACTION_NOT_FOUND));

    requireIfMatch(tx.getVersion(), ifMatch);

    // Apply updates
    TransactionEntity updated = txService.update(tx, req);
    TransactionEntity initializedTx = reloadTransaction(bookId, updated.getId());

    return ResponseEntity.ok()
        .eTag(etag(initializedTx.getVersion()))
        .body(toResponse(initializedTx));
  }

  @PostMapping("/import")
  public ResponseEntity<ImportResult> importTransactions(
      Authentication auth, @PathVariable UUID bookId, @RequestParam("file") MultipartFile file) {
    log.info(
        "IMPORT transactions: bookId={}, fileName={}, size={}",
        bookId,
        file.getOriginalFilename(),
        file.getSize());

    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    BookEntity book = bookService.requireOwned(bookId, user);

    ImportResult result = importService.importXlsx(book, file);
    log.info(
        "Import complete: bookId={}, total={}, imported={}, failed={}",
        bookId,
        result.totalRows(),
        result.importedCount(),
        result.failedCount());

    return ResponseEntity.ok(result);
  }

  @GetMapping("/export")
  public ResponseEntity<StreamingResponseBody> export(
      Authentication auth,
      @PathVariable UUID bookId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) UUID categoryId) {
    log.info(
        "EXPORT transactions: bookId={}, from={}, to={}, type={}, categoryId={}",
        bookId,
        from,
        to,
        type,
        categoryId);

    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    bookService.requireOwned(bookId, user);

    LocalDate fromDate = (from == null || from.isBlank()) ? null : LocalDate.parse(from);
    LocalDate toDate = (to == null || to.isBlank()) ? null : LocalDate.parse(to);

    TransactionType txType = null;
    if (type != null && !type.isBlank()) {
      try {
        txType = TransactionType.valueOf(type.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid transaction type");
      }
    }

    List<TransactionEntity> rows =
        txRepo.listForExport(bookId, fromDate, toDate, txType, categoryId);

    StreamingResponseBody body = out -> exportService.writeXlsx(rows, out);

    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"transactions-" + bookId + ".xlsx\"")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(body);
  }

  @DeleteMapping("/{txId}")
  public ResponseEntity<Void> delete(
      Authentication auth,
      @PathVariable UUID bookId,
      @PathVariable UUID txId,
      @RequestHeader("If-Match") String ifMatch) {
    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    bookService.requireOwned(bookId, user);

    TransactionEntity tx =
        txRepo
            .findByIdAndBook_IdAndDeletedAtIsNull(txId, bookId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, TRANSACTION_NOT_FOUND));

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
        tx.getOccurredAt(),
        tx.getTitle(),
        tx.getCategory().getId(),
        new TransactionCategoryRef(
            tx.getCategory().getId(), tx.getCategory().getName(), tx.getCategory().getType()),
        tx.getPaymentMethod(),
        tx.getNote(),
        tx.getCreatedAt(),
        tx.getUpdatedAt(),
        tx.getDeletedAt(),
        tx.getVersion() == null ? 0 : tx.getVersion());
  }

  private TransactionEntity reloadTransaction(UUID bookId, UUID txId) {
    return txRepo
        .findByIdAndBook_IdAndDeletedAtIsNull(txId, bookId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR",
                    "Transaction was saved but could not be reloaded"));
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
          List.of(Map.of("expected", String.valueOf(expected), "actual", String.valueOf(actual))));
    }
  }

  private long parseEtagVersion(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "MISSING_IF_MATCH", "If-Match header is required");
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

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Failed to serialize response");
    }
  }

  private TransactionResponse readTransactionResponse(String json) {
    try {
      return objectMapper.readValue(json, TransactionResponse.class);
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "IDEMPOTENCY_REPLAY_UNAVAILABLE",
          "Stored idempotency response is unavailable");
    }
  }
}
