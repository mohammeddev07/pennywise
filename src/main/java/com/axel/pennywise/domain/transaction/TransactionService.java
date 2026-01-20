package com.axel.pennywise.domain.transaction;

import com.axel.pennywise.api.dto.common.CursorPage;
import com.axel.pennywise.api.dto.transaction.TransactionListFilter;
import com.axel.pennywise.api.dto.transaction.TransactionUpdateRequest;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository repo;
    private final CategoryRepository categoryRepo;
    private final ObjectMapper objectMapper;

    private record TxCursor(LocalDate occurredOn, OffsetDateTime createdAt, UUID id) {}

    public TransactionEntity create(BookEntity book, CategoryEntity category, TransactionType type, long amountMinor, LocalDate occurredOn, String note) {
        log.debug("Creating transaction: bookId={}, categoryId={}, type={}, amountMinor={}, occurredOn={}",
                book.getId(), category.getId(), type, amountMinor, occurredOn);

        if (amountMinor <= 0) {
            log.warn("Invalid amount for transaction: amountMinor={}", amountMinor);
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "amountMinor must be > 0");
        }

        TransactionEntity tx = new TransactionEntity();
        tx.setBook(book);
        tx.setCategory(category);
        tx.setType(type);
        tx.setAmountMinor(amountMinor);
        tx.setOccurredOn(occurredOn);
        tx.setNote(note);

        TransactionEntity saved = repo.save(tx);
        log.info("Transaction created: transactionId={}, bookId={}, categoryId={}, type={}, amount={}",
                saved.getId(), book.getId(), category.getId(), type, amountMinor);

        return saved;
    }

    /**
     * Phase 1 complete: list with filters + cursor pagination.
     * Cursor encodes the last item’s (occurredOn, createdAt, id) as base64url(JSON).
     */
    @Transactional(readOnly = true)
    public CursorPage<TransactionEntity> list(TransactionListFilter f) {
        int pageSize = Math.clamp(f.limit(), 1, 200);
        String qNorm = (f.q() == null || f.q().isBlank()) ? null : f.q().trim();

        TxCursor c = decodeCursorOrNull(f.cursor());

        var pageable = PageRequest.of(0, pageSize + 1); // fetch 1 extra

        List<TransactionEntity> rows;
        if (c == null) {
            rows = repo.listForBookFirstPage(
                    f.bookId(),
                    f.fromDate(),
                    f.toDate(),
                    f.type(),
                    f.categoryId(),
                    qNorm,
                    pageable
            );
        } else {
            rows = repo.listForBookAfterCursor(
                    f.bookId(),
                    f.fromDate(),
                    f.toDate(),
                    f.type(),
                    f.categoryId(),
                    qNorm,
                    c.occurredOn(),
                    c.createdAt(),
                    c.id(),
                    pageable
            );
        }

        String nextCursor = null;
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
            TransactionEntity last = rows.get(rows.size() - 1);
            nextCursor = encodeCursor(new TxCursor(last.getOccurredOn(), last.getCreatedAt(), last.getId()));
        }

        return new CursorPage<>(rows, nextCursor);
    }


    @Transactional
    public TransactionEntity update(TransactionEntity tx, TransactionUpdateRequest req) {
        if (req.type() != null) tx.setType(req.type());

        if (req.amountMinor() != null) {
            if (req.amountMinor() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "amountMinor must be > 0");
            }
            tx.setAmountMinor(req.amountMinor());
        }

        if (req.occurredOn() != null) tx.setOccurredOn(req.occurredOn());
        if (req.note() != null) tx.setNote(req.note());

        if (req.categoryId() != null) {
            CategoryEntity cat = categoryRepo.findByIdAndBook_IdAndDeletedAtIsNull(
                            req.categoryId(),
                            tx.getBook().getId()
                    )
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Category not found"));

            tx.setCategory(cat);
        }

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
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Failed to encode cursor");
        }
    }

}

