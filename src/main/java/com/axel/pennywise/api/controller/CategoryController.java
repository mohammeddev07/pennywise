package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.category.CategoryCreateRequest;
import com.axel.pennywise.api.dto.category.CategoryResponse;
import com.axel.pennywise.api.dto.category.CategoryUpdateRequest;
import com.axel.pennywise.api.dto.common.ItemsResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.transaction.TransactionRepository;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import com.axel.pennywise.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/books/{bookId}/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final UserService userService;
    private final BookService bookService;
    private final CategoryRepository categoryRepo;
    private final TransactionRepository txRepo;

    private static final String LOCAL = "local";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String CONFLICT = "CONFLICT";
    private static final String LOG_USER_RESOLVED = "User resolved: userId={}";
    private static final String ETAG_MISMATCH = "ETAG_MISMATCH";
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    @GetMapping
    public ResponseEntity<ItemsResponse<CategoryResponse>> list(Authentication auth, @PathVariable UUID bookId) {
        log.info("LIST categories: bookId={}", bookId);
        try {
            UserEntity user = userService.getOrCreate(
                    auth,
                    CurrentUser.subject().orElse(LOCAL),
                    CurrentUser.email().orElse(null)
            );
            log.debug(LOG_USER_RESOLVED, user.getId());

            BookEntity book = bookService.requireOwned(bookId, user);
            log.debug("Book verified for listing categories: bookId={}", book.getId());

            List<CategoryResponse> items = categoryRepo.findAllByBook_IdAndDeletedAtIsNull(book.getId()).stream()
                    .map(this::toResponse)
                    .toList();
            log.info("Categories listed: bookId={}, count={}", bookId, items.size());

            return ResponseEntity.ok(new ItemsResponse<>(items));
        } catch (Exception e) {
            log.error("Error listing categories: bookId={}", bookId, e);
            throw e;
        }
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            Authentication auth,
            @PathVariable UUID bookId,
            @Valid @RequestBody CategoryCreateRequest req
    ) {
        log.info("CREATE category: bookId={}, type={}, name={}", bookId, req.type(), req.name());

        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        log.debug(LOG_USER_RESOLVED, user.getId());

        BookEntity book = bookService.requireOwned(bookId, user);
        log.debug("Book verified: bookId={}", book.getId());

        String normalized = normalizeName(req.name());
        if (normalized == null || normalized.isBlank()) {
            throw new com.axel.pennywise.exception.ApiException(
                    HttpStatus.BAD_REQUEST,
                    VALIDATION_ERROR,
                    "Category name is required"
            );
        }

        boolean exists = categoryRepo.existsByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(bookId, req.type(), normalized);
        if (exists) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    CONFLICT,
                    "Category name already exists for this book and type"
            );
        }

        CategoryEntity c = new CategoryEntity();
        c.setBook(book);
        c.setType(req.type());
        c.setName(normalized);
        c.setDisabled(false);
        c.setIcon(normalizeNullable(req.icon()));
        c.setColor(normalizeNullable(req.color()));

        c = categoryRepo.save(c);
        log.info("Category created: categoryId={}, bookId={}, type={}, name={}", c.getId(), bookId, c.getType(), c.getName());

        return ResponseEntity.status(201).eTag(etag(c.getVersion())).body(toResponse(c));
    }


    @PatchMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> patch(
            Authentication auth,
            @PathVariable UUID bookId,
            @PathVariable UUID categoryId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody CategoryUpdateRequest req
    ) {
        log.info("PATCH category: categoryId={}, bookId={}, ifMatch={}", categoryId, bookId, ifMatch);

        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        log.debug(LOG_USER_RESOLVED, user.getId());

        bookService.requireOwned(bookId, user);
        log.debug("Book verified: bookId={}", bookId);

        CategoryEntity c = categoryRepo.findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId)
                .orElseThrow(() -> new com.axel.pennywise.exception.ApiException(
                        HttpStatus.NOT_FOUND, NOT_FOUND, "Category not found"
                ));

        requireIfMatch(c.getVersion(), ifMatch);

        // name update with normalization + uniqueness check
        if (req.name() != null) {
            String normalized = normalizeName(req.name());
            if (normalized.isBlank()) {
                throw new com.axel.pennywise.exception.ApiException(
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR,
                        "Category name cannot be blank"
                );
            }

            boolean nameChanged = !normalized.equalsIgnoreCase(c.getName());
            if (nameChanged) {
                boolean exists = categoryRepo.existsByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(bookId, c.getType(), normalized);
                if (exists) {
                    throw new com.axel.pennywise.exception.ApiException(
                            HttpStatus.CONFLICT,
                            CONFLICT,
                            "Category name already exists for this book and type"
                    );
                }
                log.debug("Updating category name: from={}, to={}", c.getName(), normalized);
                c.setName(normalized);
            }
        }

        // disable toggle
        if (req.isDisabled() != null) {
            log.debug("Updating category disabled flag: was={}, now={}", c.isDisabled(), req.isDisabled());
            c.setDisabled(req.isDisabled());
        }

        if (req.icon() != null) {
            c.setIcon(normalizeNullable(req.icon()));
        }

        if (req.color() != null) {
            c.setColor(normalizeNullable(req.color()));
        }

        c = categoryRepo.save(c);
        log.info("Category updated: categoryId={}, bookId={}, name={}, disabled={}",
                categoryId, bookId, c.getName(), c.isDisabled());

        return ResponseEntity.ok().eTag(etag(c.getVersion())).body(toResponse(c));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            Authentication auth,
            @PathVariable UUID bookId,
            @PathVariable UUID categoryId,
            @RequestHeader("If-Match") String ifMatch
    ) {
        log.info("DELETE category: categoryId={}, bookId={}, ifMatch={}", categoryId, bookId, ifMatch);

        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        log.debug(LOG_USER_RESOLVED, user.getId());

        bookService.requireOwned(bookId, user);
        log.debug("Book verified: bookId={}", bookId);

        CategoryEntity c = categoryRepo.findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, "Category not found"));

        requireIfMatch(c.getVersion(), ifMatch);

        if (txRepo.existsByBook_IdAndCategory_IdAndDeletedAtIsNull(bookId, categoryId)) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_IN_USE", "Category has active transactions");
        }

        if (c.getDeletedAt() == null) {
            c.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        categoryRepo.save(c);

        return ResponseEntity.noContent().build();
    }

    private CategoryResponse toResponse(CategoryEntity c) {
        return new CategoryResponse(
                c.getId(),
                c.getBook().getId(),
                c.getType(),
                c.getName(),
                c.isDisabled(),
                c.getIcon(),
                c.getColor(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getDeletedAt(),
                c.getVersion() == null ? 0 : c.getVersion()
        );
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        // Trim and collapse internal whitespace
        return name.trim().replaceAll("\\s+", " ");
    }

    private String normalizeNullable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private long parseEtagVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new com.axel.pennywise.exception.ApiException(
                    HttpStatus.BAD_REQUEST,
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
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IF_MATCH",
                    "Invalid If-Match value"
            );
        }
    }

    private void requireIfMatch(Long currentVersion, String ifMatch) {
        long expected = parseEtagVersion(ifMatch);
        long actual = (currentVersion == null) ? 0L : currentVersion;
        if (expected != actual) {
            throw new com.axel.pennywise.exception.ApiException(
                    HttpStatus.PRECONDITION_FAILED,
                    ETAG_MISMATCH,
                    "Resource was modified. Re-fetch and retry."
            );
        }
    }

    private String etag(Long version) {
        long v = (version == null) ? 0L : version;
        return "\"" + v + "\"";
    }
}
