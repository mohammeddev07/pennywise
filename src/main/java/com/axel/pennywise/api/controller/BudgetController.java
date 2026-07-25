package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.budget.BudgetResponse;
import com.axel.pennywise.api.dto.budget.BudgetUpsertRequest;
import com.axel.pennywise.api.dto.common.ItemsResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.budget.BudgetService;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/books/{bookId}/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private static final String LOCAL = "local";

    private final UserService userService;
    private final BookService bookService;
    private final BudgetService budgetService;

    @GetMapping
    public ResponseEntity<ItemsResponse<BudgetResponse>> list(
            Authentication auth,
            @PathVariable UUID bookId,
            @RequestParam String month
    ) {
        UserEntity user = currentUser(auth);
        BookEntity book = bookService.requireOwned(bookId, user);
        YearMonth yearMonth = budgetService.parseMonthOrThrow(month);
        return ResponseEntity.ok(new ItemsResponse<>(budgetService.list(book, yearMonth)));
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<BudgetResponse> upsert(
            Authentication auth,
            @PathVariable UUID bookId,
            @PathVariable UUID categoryId,
            @RequestParam String month,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody BudgetUpsertRequest req
    ) {
        UserEntity user = currentUser(auth);
        BookEntity book = bookService.requireOwned(bookId, user);
        BudgetResponse response = budgetService.upsert(book, categoryId, budgetService.parseMonthOrThrow(month), req.amountMinor(), ifMatch);
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            Authentication auth,
            @PathVariable UUID bookId,
            @PathVariable UUID categoryId,
            @RequestParam String month,
            @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        UserEntity user = currentUser(auth);
        BookEntity book = bookService.requireOwned(bookId, user);
        budgetService.delete(book, categoryId, budgetService.parseMonthOrThrow(month), ifMatch);
        return ResponseEntity.noContent().build();
    }

    private UserEntity currentUser(Authentication auth) {
        return userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
    }

    private String etag(long version) {
        return "\"" + version + "\"";
    }
}
