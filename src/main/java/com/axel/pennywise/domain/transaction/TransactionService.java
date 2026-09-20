package com.axel.pennywise.domain.transaction;

import com.axel.pennywise.api.dto.common.CursorPage;
import com.axel.pennywise.api.dto.transaction.TransactionListFilter;
import com.axel.pennywise.api.dto.transaction.TransactionUpdateRequest;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.category.CategoryType;
import com.axel.pennywise.domain.common.MoneyLimits;
import com.axel.pennywise.domain.summary.CacheEvictionService;
import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
  private final TransactionRepository repo;
  private final CategoryRepository categoryRepo;
  private final ObjectMapper objectMapper;
  private final CacheEvictionService cacheEvictionService;

  private record TxCursor(LocalDate occurredOn, OffsetDateTime createdAt, UUID id) {}

  public TransactionEntity create(
      BookEntity book,
      CategoryEntity category,
      TransactionType type,
      long amountMinor,
      LocalDate occurredOn,
      String note) {
    return create(book, category, type, amountMinor, occurredOn, note, null, null, null);
  }

  public TransactionEntity create(
      BookEntity book,
      CategoryEntity category,
      TransactionType type,
      long amountMinor,
      LocalDate occurredOn,
      String note,
      String title,
      PaymentMethod paymentMethod,
      OffsetDateTime occurredAt) {
    log.debug(
        "Creating transaction: bookId={}, categoryId={}, type={}, amountMinor={}, occurredOn={}",
        book.getId(),
        category.getId(),
        type,
        amountMinor,
        occurredOn);

    requireValidAmount(amountMinor);

    validateCategoryType(category, type);

    LocalDate resolvedOccurredOn = resolveOccurredOn(book, occurredOn, occurredAt);
    if (resolvedOccurredOn == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "occurredOn or occurredAt is required");
    }

    requireWithinAggregate(book.getId(), type, amountMinor, 0L);

    TransactionEntity tx = new TransactionEntity();
    tx.setBook(book);
    tx.setCategory(category);
    tx.setType(type);
    tx.setAmountMinor(amountMinor);
    tx.setOccurredOn(resolvedOccurredOn);
    tx.setOccurredAt(resolveOccurredAt(book, resolvedOccurredOn, occurredAt));
    tx.setTitle(normalizeNullable(title));
    tx.setPaymentMethod(paymentMethod);
    tx.setNote(normalizeNullable(note));

    TransactionEntity saved = repo.save(tx);
    log.info(
        "Transaction created: transactionId={}, bookId={}, categoryId={}, type={}, amount={}",
        saved.getId(),
        book.getId(),
        category.getId(),
        type,
        amountMinor);

    cacheEvictionService.evictBook(book.getId());
    return saved;
  }

  public TransactionEntity create(
      BookEntity book,
      CategoryEntity category,
      TransactionType type,
      long amountMinor,
      LocalDate occurredOn,
      String note,
      String title,
      PaymentMethod paymentMethod,
      OffsetDateTime occurredAt,
      String externalId) {
    TransactionEntity saved =
        create(
            book, category, type, amountMinor, occurredOn, note, title, paymentMethod, occurredAt);
    if (externalId != null) {
      saved.setExternalId(externalId);
      saved = repo.save(saved);
    }
    return saved;
  }

  /**
   * Phase 1 complete: list with filters + cursor pagination. Cursor encodes the last item’s
   * (occurredOn, createdAt, id) as base64url(JSON).
   */
  @Transactional(readOnly = true)
  public CursorPage<TransactionEntity> list(TransactionListFilter f) {
    int pageSize = Math.clamp(f.limit(), 1, 200);
    String qNorm = (f.q() == null || f.q().isBlank()) ? null : f.q().trim();
    String searchLike = (qNorm == null) ? null : "%" + qNorm.toLowerCase(Locale.ROOT) + "%";
    TransactionType qType = parseEnumOrNull(TransactionType.class, qNorm);
    PaymentMethod qPaymentMethod = parseEnumOrNull(PaymentMethod.class, qNorm);
    Long amountSearch = parseLongOrNull(qNorm);

    TxCursor c = decodeCursorOrNull(f.cursor());

    var pageable = PageRequest.of(0, pageSize + 1); // fetch 1 extra

    List<TransactionEntity> rows;
    if (c == null) {
      rows =
          repo.listForBookFirstPage(
              f.bookId(),
              f.fromDate(),
              f.toDate(),
              f.type(),
              f.categoryId(),
              searchLike,
              qType,
              qPaymentMethod,
              amountSearch,
              pageable);
    } else {
      rows =
          repo.listForBookAfterCursor(
              f.bookId(),
              f.fromDate(),
              f.toDate(),
              f.type(),
              f.categoryId(),
              searchLike,
              qType,
              qPaymentMethod,
              amountSearch,
              c.occurredOn(),
              c.createdAt(),
              c.id(),
              pageable);
    }

    String nextCursor = null;
    if (rows.size() > pageSize) {
      rows = rows.subList(0, pageSize);
      TransactionEntity last = rows.get(rows.size() - 1);
      nextCursor =
          encodeCursor(new TxCursor(last.getOccurredOn(), last.getCreatedAt(), last.getId()));
    }

    return new CursorPage<>(rows, nextCursor);
  }

  /**
   * Applies a PATCH. Omitted properties are unchanged; explicit null clears title/note/
   * paymentMethod (required fields reject null at deserialization). The date pair is resolved
   * together: occurredOn alone resets occurredAt to book-local midnight, occurredAt alone derives
   * occurredOn in the book timezone, both together must agree. createdAt and id are never touched;
   * updatedAt and version advance only if Hibernate finds a real change at flush, so a no-op PATCH
   * returns the same timestamps and version.
   */
  @Transactional
  public TransactionEntity update(TransactionEntity tx, TransactionUpdateRequest req) {
    TransactionType resolvedType = req.type() == null ? tx.getType() : required(req.type(), "type");
    CategoryEntity resolvedCategory = tx.getCategory();
    if (req.categoryId() != null) {
      UUID categoryId = required(req.categoryId(), "categoryId");
      resolvedCategory =
          categoryRepo
              .findByIdAndBook_IdAndDeletedAtIsNull(categoryId, tx.getBook().getId())
              .orElseThrow(
                  () -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Category not found"));
    }
    validateCategoryType(resolvedCategory, resolvedType);

    long resolvedAmount = tx.getAmountMinor();
    if (req.amountMinor() != null) {
      resolvedAmount = required(req.amountMinor(), "amountMinor");
      requireValidAmount(resolvedAmount);
    }
    if (req.type() != null || req.amountMinor() != null) {
      // The row's own current amount is already inside the stored total when the type is unchanged.
      long alreadyCounted = resolvedType == tx.getType() ? tx.getAmountMinor() : 0L;
      requireWithinAggregate(tx.getBook().getId(), resolvedType, resolvedAmount, alreadyCounted);
    }

    tx.setType(resolvedType);
    tx.setCategory(resolvedCategory);
    tx.setAmountMinor(resolvedAmount);

    LocalDate reqOccurredOn =
        req.occurredOn() == null ? null : required(req.occurredOn(), "occurredOn");
    OffsetDateTime reqOccurredAt =
        req.occurredAt() == null ? null : required(req.occurredAt(), "occurredAt");
    if (reqOccurredOn != null || reqOccurredAt != null) {
      LocalDate occurredOn = resolveOccurredOn(tx.getBook(), reqOccurredOn, reqOccurredAt);
      tx.setOccurredOn(occurredOn);
      tx.setOccurredAt(resolveOccurredAt(tx.getBook(), occurredOn, reqOccurredAt));
    }

    if (req.title() != null) tx.setTitle(normalizeNullable(req.title().orElse(null)));
    if (req.note() != null) tx.setNote(normalizeNullable(req.note().orElse(null)));
    if (req.paymentMethod() != null) tx.setPaymentMethod(req.paymentMethod().orElse(null));

    TransactionEntity saved = repo.save(tx);
    cacheEvictionService.evictBook(saved.getBook().getId());
    return saved;
  }

  /** A required PATCH field was sent as explicit null. */
  private static <T> T required(java.util.Optional<T> field, String name) {
    return field.orElseThrow(
        () ->
            new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Field must not be null: " + name,
                List.of(Map.of("field", name, "message", "must not be null"))));
  }

  @Transactional
  public void softDelete(TransactionEntity tx) {
    if (tx.getDeletedAt() == null) {
      tx.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }
    repo.save(tx);
    cacheEvictionService.evictBook(tx.getBook().getId());
  }

  private TxCursor decodeCursorOrNull(String cursor) {
    if (cursor == null || cursor.isBlank()) return null;
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(cursor);
      return objectMapper.readValue(decoded, TxCursor.class);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Invalid cursor");
    }
  }

  private String encodeCursor(TxCursor c) {
    try {
      byte[] json = objectMapper.writeValueAsBytes(c);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Failed to encode cursor");
    }
  }

  /**
   * occurredOn is the canonical book-local ledger date; occurredAt is the event instant. When both
   * are supplied they must name the same book-local day - otherwise the row would sort under one
   * day and display another. Rows written before this rule (V4 backfill, date-only imports) are
   * never reinterpreted; only new requests are checked.
   */
  private LocalDate resolveOccurredOn(
      BookEntity book, LocalDate occurredOn, OffsetDateTime occurredAt) {
    if (occurredAt == null) return occurredOn;
    LocalDate derived = occurredAt.atZoneSameInstant(zoneIdFor(book)).toLocalDate();
    if (occurredOn != null && !occurredOn.equals(derived)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "occurredOn must match occurredAt in the book timezone ("
              + book.getTimezone()
              + "): occurredAt falls on "
              + derived);
    }
    return occurredOn != null ? occurredOn : derived;
  }

  private OffsetDateTime resolveOccurredAt(
      BookEntity book, LocalDate occurredOn, OffsetDateTime occurredAt) {
    if (occurredAt != null) return occurredAt.withOffsetSameInstant(ZoneOffset.UTC);
    return occurredOn.atStartOfDay(zoneIdFor(book)).toOffsetDateTime();
  }

  private void requireValidAmount(long amountMinor) {
    if (amountMinor <= 0) {
      log.warn("Invalid amount for transaction: amountMinor={}", amountMinor);
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "amountMinor must be > 0");
    }
    if (amountMinor > MoneyLimits.MAX_TRANSACTION_AMOUNT_MINOR) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "amountMinor must be <= " + MoneyLimits.MAX_TRANSACTION_AMOUNT_MINOR);
    }
  }

  /**
   * Keeps every per-book, per-type total within {@link MoneyLimits#MAX_AGGREGATE_AMOUNT_MINOR} so
   * summaries stay exact when read as a JavaScript number. {@code alreadyCounted} is this row's
   * amount when it is already part of the stored total (an edit).
   */
  private void requireWithinAggregate(
      UUID bookId, TransactionType type, long amountMinor, long alreadyCounted) {
    long total = repo.sumAmountByType(bookId, type) - alreadyCounted;
    if (amountMinor > MoneyLimits.MAX_AGGREGATE_AMOUNT_MINOR - total) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "Book total for " + type + " would exceed the supported maximum");
    }
  }

  private ZoneId zoneIdFor(BookEntity book) {
    try {
      return ZoneId.of(book.getTimezone());
    } catch (DateTimeException | NullPointerException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid book timezone");
    }
  }

  private void validateCategoryType(CategoryEntity category, TransactionType transactionType) {
    CategoryType expectedCategoryType = CategoryType.valueOf(transactionType.name());
    if (category.getType() != expectedCategoryType) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Category type must match transaction type");
    }
  }

  private String normalizeNullable(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }

  private <E extends Enum<E>> E parseEnumOrNull(Class<E> enumType, String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Long parseLongOrNull(String value) {
    if (value == null || !value.matches("\\d+")) return null;
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
