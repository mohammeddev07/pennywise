package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.transaction.TransactionAnalysisResponse;
import com.axel.pennywise.api.dto.transaction.TransactionResponse;
import com.axel.pennywise.api.dto.transaction.TransactionSearchResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.summary.TransactionAnalysisService;
import com.axel.pennywise.domain.transaction.query.FilteredExportService;
import com.axel.pennywise.domain.transaction.query.QueryFingerprint;
import com.axel.pennywise.domain.transaction.query.QueryRequestParser;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Query;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Search;
import com.axel.pennywise.domain.transaction.query.TransactionQueryService;
import com.axel.pennywise.domain.transaction.query.TransactionQueryService.SearchResult;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import com.axel.pennywise.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Filter-AST endpoints (P1.2/P1.3). The legacy GET list/export/summary routes stay in {@link
 * TransactionController} and {@link SummaryController} untouched. Book ownership is resolved first,
 * so an unowned or unknown book id is always 404 and never reveals anything about the filter.
 * Filter values are user data and are never logged; only the fingerprint is.
 */
@Slf4j
@RestController
@RequestMapping("/v1/books/{bookId}/transactions")
@RequiredArgsConstructor
public class TransactionQueryController {

  private static final String LOCAL = "local";
  private static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final UserService userService;
  private final BookService bookService;
  private final QueryRequestParser parser;
  private final TransactionQueryService queryService;
  private final TransactionAnalysisService analysisService;
  private final FilteredExportService exportService;

  @PostMapping("/search")
  public ResponseEntity<TransactionSearchResponse> search(
      Authentication auth, @PathVariable UUID bookId, HttpServletRequest request) {
    BookEntity book = ownedBook(auth, bookId);
    Search req = parser.parseSearch(readBody(request));

    SearchResult r = queryService.search(book.getId(), req.query(), req.page());
    String fingerprint = QueryFingerprint.of(req.query().filter(), req.query().sort());
    log.info(
        "SEARCH transactions: bookId={}, fingerprint={}, total={}",
        bookId,
        fingerprint,
        r.totalCount());

    var items = r.items().stream().map(t -> TransactionResponse.from(t, bookId)).toList();
    return ResponseEntity.ok(
        new TransactionSearchResponse(
            items,
            r.totalCount(),
            new TransactionSearchResponse.PageInfo(
                req.page().offset(), req.page().limit(), r.hasMore()),
            fingerprint));
  }

  @PostMapping("/analyze")
  public ResponseEntity<TransactionAnalysisResponse> analyze(
      Authentication auth, @PathVariable UUID bookId, HttpServletRequest request) {
    BookEntity book = ownedBook(auth, bookId);
    var req = parser.parseAnalyze(readBody(request));

    TransactionAnalysisResponse body = analysisService.analyze(book, req);
    log.info(
        "ANALYZE transactions: bookId={}, fingerprint={}, matched={}",
        bookId,
        body.queryFingerprint(),
        body.matchedCount());
    return ResponseEntity.ok(body);
  }

  @PostMapping("/export/query")
  public ResponseEntity<StreamingResponseBody> exportQuery(
      Authentication auth, @PathVariable UUID bookId, HttpServletRequest request)
      throws IOException {
    BookEntity book = ownedBook(auth, bookId);
    Query query = parser.parseQueryBody(readBody(request));

    Path file = exportService.exportToTempFile(book, query);
    log.info(
        "EXPORT query: bookId={}, fingerprint={}",
        bookId,
        QueryFingerprint.of(query.filter(), query.sort()));

    long size;
    try {
      size = Files.size(file);
    } catch (IOException e) {
      Files.deleteIfExists(file);
      throw e;
    }
    StreamingResponseBody body =
        out -> {
          try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(out);
          } finally {
            Files.deleteIfExists(file);
          }
        };
    return ResponseEntity.ok()
        .header(
            "Content-Disposition",
            "attachment; filename=\"transactions-" + bookId + "-filtered.xlsx\"")
        .contentType(XLSX)
        .contentLength(size)
        .body(body);
  }

  private BookEntity ownedBook(Authentication auth, UUID bookId) {
    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    return bookService.requireOwned(bookId, user);
  }

  /** Reads at most MAX_BODY_BYTES; chunked bodies without a Content-Length are bounded too. */
  private static byte[] readBody(HttpServletRequest request) {
    if (request.getContentLengthLong() > QueryRequestParser.MAX_BODY_BYTES) throw tooLarge();
    try {
      byte[] body = request.getInputStream().readNBytes(QueryRequestParser.MAX_BODY_BYTES + 1);
      if (body.length > QueryRequestParser.MAX_BODY_BYTES) throw tooLarge();
      return body;
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Unreadable request body");
    }
  }

  private static ApiException tooLarge() {
    return new ApiException(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "REQUEST_TOO_LARGE",
        "Request body exceeds " + QueryRequestParser.MAX_BODY_BYTES + " bytes");
  }
}
