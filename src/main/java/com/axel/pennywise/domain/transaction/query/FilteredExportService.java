package com.axel.pennywise.domain.transaction.query;

import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.transaction.TransactionEntity;
import com.axel.pennywise.domain.transaction.TransactionExportService;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Query;
import com.axel.pennywise.exception.ApiException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports every row matching a filter (not just a visible page) to a temp .xlsx file.
 *
 * <p>The whole export runs in one repeatable-read transaction, so the row-count check, the chunked
 * reads and the sort order all see one snapshot. Rows are read {@value #CHUNK} at a time and the
 * persistence context is cleared between chunks, POI's SXSSF window keeps 100 rows in memory, so
 * memory stays bounded. Writing to a file first (instead of streaming straight to the response)
 * lets limit violations surface as a normal JSON error before any response byte is sent.
 */
@Service
@RequiredArgsConstructor
public class FilteredExportService {

  public static final int MAX_ROWS = 50_000;
  public static final long MAX_BYTES = 20L * 1024 * 1024;
  static final int CHUNK = 1000;

  private final TransactionQueryRepository repo;
  private final TransactionExportService writer;

  /** Caller must delete the returned file once it has been sent. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Path exportToTempFile(BookEntity book, Query query) {
    UUID bookId = book.getId();
    long total = repo.count(bookId, query.filter());
    if (total > MAX_ROWS) {
      throw tooLarge(
          "The filter matches "
              + total
              + " transactions; the export limit is "
              + MAX_ROWS
              + " rows.");
    }

    Path tmp = null;
    try {
      tmp = Files.createTempFile("pennywise-export-", ".xlsx");
      try (OutputStream out = Files.newOutputStream(tmp)) {
        writer.writeXlsx(chunks(bookId, query), book.getCurrencyCode(), out);
      }
      if (Files.size(tmp) > MAX_BYTES) {
        throw tooLarge("The export file would exceed " + (MAX_BYTES / 1024 / 1024) + " MB.");
      }
      return tmp;
    } catch (IOException e) {
      deleteQuietly(tmp);
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Failed to write export file");
    } catch (RuntimeException e) {
      deleteQuietly(tmp);
      throw e;
    }
  }

  private static ApiException tooLarge(String detail) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "EXPORT_TOO_LARGE",
        detail + " Narrow the filter (for example a shorter date range) and try again.");
  }

  private static void deleteQuietly(Path p) {
    if (p == null) return;
    try {
      Files.deleteIfExists(p);
    } catch (IOException ignored) {
      // best effort; the OS temp cleaner will get it
    }
  }

  private Iterable<TransactionEntity> chunks(UUID bookId, Query query) {
    return () ->
        new Iterator<>() {
          private Iterator<TransactionEntity> current = List.<TransactionEntity>of().iterator();
          private int offset = 0;
          private boolean exhausted = false;

          @Override
          public boolean hasNext() {
            if (current.hasNext()) return true;
            if (exhausted) return false;
            repo.clear(); // rows of the previous chunk are already written
            List<TransactionEntity> chunk =
                repo.page(bookId, query.filter(), query.sort(), offset, CHUNK);
            offset += chunk.size();
            exhausted = chunk.size() < CHUNK;
            current = chunk.iterator();
            return current.hasNext();
          }

          @Override
          public TransactionEntity next() {
            if (!hasNext()) throw new NoSuchElementException();
            return current.next();
          }
        };
  }
}
