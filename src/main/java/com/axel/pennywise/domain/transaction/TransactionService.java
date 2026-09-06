package com.axel.pennywise.domain.transaction;

import com.axel.pennywise.api.dto.common.CursorPage;
import com.axel.pennywise.api.dto.transaction.TransactionListFilter;
import com.axel.pennywise.api.dto.transaction.TransactionUpdateRequest;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.category.CategoryType;
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

    if (amountMinor <= 0) {
      log.warn("Invalid amount for transaction: amountMinor={}", amountMinor);
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "amountMinor must be > 0");
    }

    validateCategoryType(category, type);

    LocalDate resolvedOccurredOn = resolveOccurredOn(book, occurredOn, occurredAt);
    if (resolvedOccurredOn == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "occurredOn or occurredAt is required");
    }

    TransactionEntity tx = new TransactionEntity();
    tx.setBook(book);
    tx.setCategory(category);
    tx.setType(type);
    tx.setAmountMinor(amountMinor);
    tx.setOccurredOn(resolvedOccurredOn);
    tx.setOccurredAt(resolveOccurredAt(book, resolvedOccurredOn, occurredAt));
    tx.setTitle(normalizeNullable(title));
    tx.setPaymentMethod(paymentMethod);
    tx.setNote(note);

    TransactionEntity saved = repo.save(tx);
    log.info(
        "Transaction created: transactionId={}, bookId={}, categoryId={}, type={}, amount={}",
        saved.getId(),
        book.getId(),
        category.getId(),
        type,
        amountMinor);

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

  @Transactional
  public TransactionEntity update(TransactionEntity tx, TransactionUpdateRequest req) {
    TransactionType resolvedType = req.type() == null ? tx.getType() : req.type();
    CategoryEntity resolvedCategory = tx.getCategory();
    if (req.categoryId() != null) {
      resolvedCategory =
          categoryRepo
              .findByIdAndBook_IdAndDeletedAtIsNull(req.categoryId(), tx.getBook().getId())
              .orElseThrow(
                  () -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Category not found"));
    }
    validateCategoryType(resolvedCategory, resolvedType);

    tx.setType(resolvedType);
    tx.setCategory(resolvedCategory);

    if (req.amountMinor() != null) {
      if (req.amountMinor() <= 0) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "amountMinor must be > 0");
      }
      tx.setAmountMinor(req.amountMinor());
    }

    if (req.occurredOn() != null) {
      tx.setOccurredOn(req.occurredOn());
      tx.setOccurredAt(resolveOccurredAt(tx.getBook(), req.occurredOn(), req.occurredAt()));
    } else if (req.occurredAt() != null) {
      tx.setOccurredAt(req.occurredAt());
      tx.setOccurredOn(resolveOccurredOn(tx.getBook(), null, req.occurredAt()));
    }

    if (req.title() != null) tx.setTitle(normalizeNullable(req.title()));
    if (req.paymentMethod() != null) tx.setPaymentMethod(req.paymentMethod());
    if (req.note() != null) tx.setNote(req.note());

    return repo.save(tx);
  }

  @Transactional
  public void softDelete(TransactionEntity tx) {
    if (tx.getDeletedAt() == null) {
      tx.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }
    repo.save(tx);
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

  private LocalDate resolveOccurredOn(
      BookEntity book, LocalDate occurredOn, OffsetDateTime occurredAt) {
    if (occurredOn != null) return occurredOn;
    return occurredAt == null ? null : occurredAt.atZoneSameInstant(zoneIdFor(book)).toLocalDate();
  }

  private OffsetDateTime resolveOccurredAt(
      BookEntity book, LocalDate occurredOn, OffsetDateTime occurredAt) {
    if (occurredAt != null) return occurredAt;
    return occurredOn.atStartOfDay(zoneIdFor(book)).toOffsetDateTime();
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
