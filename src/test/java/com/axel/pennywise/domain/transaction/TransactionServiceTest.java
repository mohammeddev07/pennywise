package com.axel.pennywise.domain.transaction;

import com.axel.pennywise.api.dto.common.CursorPage;
import com.axel.pennywise.api.dto.transaction.TransactionListFilter;
import com.axel.pennywise.api.dto.transaction.TransactionUpdateRequest;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository repo;
    @Mock
    private CategoryRepository categoryRepo;

    private TransactionService service;

    private UUID bookId;
    private UUID txId;

    private BookEntity book;
    private CategoryEntity category;

    @BeforeEach
    void setUp() {
        // Use a real ObjectMapper so cursor encode/decode works deterministically
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new TransactionService(repo, categoryRepo, objectMapper);

        bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID catId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        txId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        book = new BookEntity();
        book.setId(bookId);

        category = new CategoryEntity();
        category.setId(catId);
        category.setBook(book);
    }

    private TransactionEntity tx(UUID id, LocalDate occurredOn, OffsetDateTime createdAt) {
        TransactionEntity t = new TransactionEntity();
        t.setId(id);
        t.setBook(book);
        t.setCategory(category);
        t.setType(TransactionType.EXPENSE);
        t.setAmountMinor(3000L);
        t.setOccurredOn(occurredOn);
        t.setCreatedAt(createdAt);
        t.setUpdatedAt(createdAt);
        t.setNote("note");
        t.setVersion(0L);
        return t;
    }

    // -----------------------
    // create()
    // -----------------------

    @Test
    void create_savesAndReturnsEntity() {
        LocalDate occurredOn = LocalDate.of(2026, 1, 1);

        TransactionEntity saved = tx(txId, occurredOn, OffsetDateTime.now(ZoneOffset.UTC));
        when(repo.save(any(TransactionEntity.class))).thenReturn(saved);

        TransactionEntity result = service.create(book, category, TransactionType.EXPENSE, 3000L, occurredOn, "Lunch");

        assertSame(saved, result);

        verify(repo).save(argThat(t ->
                t.getBook() == book &&
                        t.getCategory() == category &&
                        t.getType() == TransactionType.EXPENSE &&
                        Long.valueOf(3000L).equals(t.getAmountMinor()) &&
                        occurredOn.equals(t.getOccurredOn()) &&
                        "Lunch".equals(t.getNote())
        ));
        verifyNoMoreInteractions(repo, categoryRepo);
    }

    @Test
    void create_throwsBadRequest_whenAmountNonPositive() {
        ApiException ex = assertThrows(ApiException.class, () ->
                service.create(book, category, TransactionType.EXPENSE, 0L, LocalDate.of(2026, 1, 1), "x")
        );

        assertEquals("amountMinor must be > 0", ex.getMessage());
        verifyNoInteractions(repo, categoryRepo);
    }

    // -----------------------
    // update()
    // -----------------------

    @Test
    void update_updatesFieldsAndSaves() {
        TransactionEntity existing = tx(txId, LocalDate.of(2026, 1, 1), OffsetDateTime.now(ZoneOffset.UTC));

        TransactionUpdateRequest req = new TransactionUpdateRequest(
                TransactionType.INCOME,
                9000L,
                LocalDate.of(2026, 1, 15),
                null,
                "Updated note"
        );

        when(repo.save(existing)).thenReturn(existing);

        TransactionEntity result = service.update(existing, req);

        assertSame(existing, result);
        assertEquals(TransactionType.INCOME, existing.getType());
        assertEquals(9000L, existing.getAmountMinor());
        assertEquals(LocalDate.of(2026, 1, 15), existing.getOccurredOn());
        assertEquals("Updated note", existing.getNote());

        verify(repo).save(existing);
        verifyNoMoreInteractions(repo, categoryRepo);
    }

    @Test
    void update_throwsBadRequest_whenAmountNonPositive() {
        TransactionEntity existing = tx(txId, LocalDate.of(2026, 1, 1), OffsetDateTime.now(ZoneOffset.UTC));

        TransactionUpdateRequest req = new TransactionUpdateRequest(
                null,
                0L,
                null,
                null,
                null
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.update(existing, req));

        assertEquals("amountMinor must be > 0", ex.getMessage());
        verifyNoInteractions(repo, categoryRepo);
    }

    @Test
    void update_changesCategory_whenProvidedAndFound() {
        TransactionEntity existing = tx(txId, LocalDate.of(2026, 1, 1), OffsetDateTime.now(ZoneOffset.UTC));

        UUID newCatId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        CategoryEntity newCat = new CategoryEntity();
        newCat.setId(newCatId);
        newCat.setBook(book);

        when(categoryRepo.findByIdAndBook_IdAndDeletedAtIsNull(newCatId, bookId))
                .thenReturn(Optional.of(newCat));
        when(repo.save(existing)).thenReturn(existing);

        TransactionUpdateRequest req = new TransactionUpdateRequest(
                null, null, null, newCatId, null
        );

        TransactionEntity result = service.update(existing, req);

        assertSame(existing, result);
        assertSame(newCat, existing.getCategory());

        verify(categoryRepo).findByIdAndBook_IdAndDeletedAtIsNull(newCatId, bookId);
        verify(repo).save(existing);
        verifyNoMoreInteractions(repo, categoryRepo);
    }

    @Test
    void update_throwsNotFound_whenCategoryMissing() {
        TransactionEntity existing = tx(txId, LocalDate.of(2026, 1, 1), OffsetDateTime.now(ZoneOffset.UTC));

        UUID newCatId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        when(categoryRepo.findByIdAndBook_IdAndDeletedAtIsNull(newCatId, bookId))
                .thenReturn(Optional.empty());

        TransactionUpdateRequest req = new TransactionUpdateRequest(
                null, null, null, newCatId, null
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.update(existing, req));
        assertEquals("Category not found", ex.getMessage());

        verify(categoryRepo).findByIdAndBook_IdAndDeletedAtIsNull(newCatId, bookId);
        verifyNoMoreInteractions(categoryRepo);
        verifyNoInteractions(repo);
    }

    // -----------------------
    // softDelete()
    // -----------------------

    @Test
    void softDelete_setsDeletedAtIfNull_andSaves() {
        TransactionEntity existing = tx(txId, LocalDate.of(2026, 1, 1), OffsetDateTime.now(ZoneOffset.UTC));
        existing.setDeletedAt(null);

        when(repo.save(existing)).thenReturn(existing);

        service.softDelete(existing);

        assertNotNull(existing.getDeletedAt());
        verify(repo).save(existing);
        verifyNoMoreInteractions(repo, categoryRepo);
    }

    @Test
    void softDelete_doesNotOverwriteDeletedAt_ifAlreadySet() {
        TransactionEntity existing = tx(txId, LocalDate.of(2026, 1, 1), OffsetDateTime.now(ZoneOffset.UTC));
        OffsetDateTime already = OffsetDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC);
        existing.setDeletedAt(already);

        when(repo.save(existing)).thenReturn(existing);

        service.softDelete(existing);

        assertEquals(already, existing.getDeletedAt());
        verify(repo).save(existing);
        verifyNoMoreInteractions(repo, categoryRepo);
    }

    // -----------------------
    // list()
    // -----------------------

    @Test
    void list_firstPage_returnsRows_andGeneratesNextCursorWhenMoreThanPageSize() {
        // limit=2, repo returns 3 => nextCursor should be present, items trimmed to 2
        int limit = 2;

        // Build 3 rows ordered as service expects (last row used for cursor)
        TransactionEntity r1 = tx(UUID.fromString("10000000-0000-0000-0000-000000000001"),
                LocalDate.of(2026, 1, 10), OffsetDateTime.parse("2026-01-10T10:00:00Z"));
        TransactionEntity r2 = tx(UUID.fromString("10000000-0000-0000-0000-000000000002"),
                LocalDate.of(2026, 1, 9), OffsetDateTime.parse("2026-01-09T10:00:00Z"));
        TransactionEntity r3 = tx(UUID.fromString("10000000-0000-0000-0000-000000000003"),
                LocalDate.of(2026, 1, 8), OffsetDateTime.parse("2026-01-08T10:00:00Z"));

        when(repo.listForBookFirstPage(
                eq(bookId),
                isNull(), isNull(),
                isNull(), isNull(),
                isNull(),
                any(Pageable.class)
        )).thenReturn(List.of(r1, r2, r3));

        // Build filter (adjust constructor/order to match your TransactionListFilter)
        TransactionListFilter f = new TransactionListFilter(
                bookId,
                null, null,
                null,
                null,
                null,
                limit,
                null,
                null, null, null
        );

        CursorPage<TransactionEntity> page = service.list(f);

        assertEquals(2, page.items().size());
        assertNotNull(page.nextCursor());

        verify(repo).listForBookFirstPage(
                eq(bookId),
                isNull(), isNull(),
                isNull(), isNull(),
                isNull(),
                any(Pageable.class)
        );
        verifyNoMoreInteractions(repo, categoryRepo);
    }

    @Test
    void list_afterCursor_usesAfterCursorRepoMethod() {
        int limit = 2;

        // Create 2 rows (no next cursor needed)
        TransactionEntity r1 = tx(UUID.fromString("20000000-0000-0000-0000-000000000001"),
                LocalDate.of(2026, 1, 10), OffsetDateTime.parse("2026-01-10T10:00:00Z"));
        TransactionEntity r2 = tx(UUID.fromString("20000000-0000-0000-0000-000000000002"),
                LocalDate.of(2026, 1, 9), OffsetDateTime.parse("2026-01-09T10:00:00Z"));

        when(repo.listForBookFirstPage(eq(bookId), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(r1, r2, tx(UUID.fromString("20000000-0000-0000-0000-000000000003"),
                        LocalDate.of(2026, 1, 8), OffsetDateTime.parse("2026-01-08T10:00:00Z"))));

        TransactionListFilter seed = new TransactionListFilter(bookId, null, null, null, null, null, 2, null, null, null, null);
        String cursor = service.list(seed).nextCursor();
        assertNotNull(cursor);

        // Now set up after-cursor behavior
        reset(repo);
        when(repo.listForBookAfterCursor(
                eq(bookId),
                isNull(), isNull(),
                isNull(), isNull(),
                isNull(),
                any(LocalDate.class),
                any(OffsetDateTime.class),
                any(UUID.class),
                any(Pageable.class)
        )).thenReturn(List.of(r1, r2));

        TransactionListFilter f = new TransactionListFilter(bookId, null, null, null, null, null, limit, cursor, null, null, null);
        CursorPage<TransactionEntity> page = service.list(f);

        assertEquals(2, page.items().size());
        assertNull(page.nextCursor());

        verify(repo).listForBookAfterCursor(
                eq(bookId),
                isNull(), isNull(),
                isNull(), isNull(),
                isNull(),
                any(LocalDate.class),
                any(OffsetDateTime.class),
                any(UUID.class),
                any(Pageable.class)
        );
        verifyNoMoreInteractions(repo, categoryRepo);
    }

    @Test
    void list_throwsBadRequest_whenCursorInvalid() {
        TransactionListFilter f = new TransactionListFilter(bookId, null, null, null, null, null, 50, "not-base64", null, null, null);

        ApiException ex = assertThrows(ApiException.class, () -> service.list(f));
        assertEquals("Invalid cursor", ex.getMessage());

        verifyNoInteractions(repo, categoryRepo);
    }
}

