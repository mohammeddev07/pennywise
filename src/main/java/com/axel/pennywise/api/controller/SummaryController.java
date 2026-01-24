package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.summary.BalanceResponse;
import com.axel.pennywise.api.dto.summary.MonthlySummaryResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.summary.SummaryService;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/books/{bookId}")
@RequiredArgsConstructor
public class SummaryController {

    private final UserService userService;
    private final BookService bookService;
    private final SummaryService summaryService;

    private static final String LOCAL = "local";
    private static final String LOG_USER_RESOLVED = "User resolved: userId={}";
    private static final String LOG_BOOKS_RESOLVED = "Book verified: bookId={}, currency={}";

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> balance(Authentication auth, @PathVariable UUID bookId) {
        log.info("GET balance: bookId={}", bookId);
        try {
            UserEntity user = userService.getOrCreate(
                    auth,
                    CurrentUser.subject().orElse(LOCAL),
                    CurrentUser.email().orElse(null)
            );
            log.debug(LOG_USER_RESOLVED, user.getId());

            BookEntity book = bookService.requireOwned(bookId, user);
            log.debug(LOG_BOOKS_RESOLVED, book.getId(), book.getCurrencyCode());

            BalanceResponse response = summaryService.balance(book);
            log.debug("Balance computed: bookId={}, balance={}", bookId, book.getOpeningBalanceMinor());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving balance: bookId={}", bookId, e);
            throw e;
        }
    }

    @GetMapping("/summary/monthly")
    public ResponseEntity<MonthlySummaryResponse> monthly(Authentication auth, @PathVariable UUID bookId, @RequestParam String month) {
        log.info("GET monthly summary: bookId={}, month={}", bookId, month);
        try {
            UserEntity user = userService.getOrCreate(
                    auth,
                    CurrentUser.subject().orElse(LOCAL),
                    CurrentUser.email().orElse(null)
            );
            log.debug(LOG_USER_RESOLVED, user.getId());

            BookEntity book = bookService.requireOwned(bookId, user);
            log.debug(LOG_BOOKS_RESOLVED, book.getId(), book.getCurrencyCode());

            YearMonth ym = summaryService.parseMonthOrThrow(month);
            MonthlySummaryResponse response = summaryService.monthly(book, ym);
            log.debug("Monthly summary compiled: bookId={}, month={}, income={}, expense={}", bookId, month, 0L, 0L);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving monthly summary: bookId={}, month={}", bookId, month, e);
            throw e;
        }
    }
}

