package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.export.ExportCreateResponse;
import com.axel.pennywise.api.dto.export.ExportDownloadResponse;
import com.axel.pennywise.api.dto.export.ExportResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.export.ExportJobEntity;
import com.axel.pennywise.domain.export.ExportJobRepository;
import com.axel.pennywise.domain.export.ExportService;
import com.axel.pennywise.domain.export.ExportStatus;
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

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/books/{bookId}/exports")
@RequiredArgsConstructor
public class ExportController {

    private final UserService userService;
    private final BookService bookService;
    private final ExportJobRepository exportRepo;
    private final ExportService exportService;

    private static final String LOCAL = "local";

    @PostMapping("/csv")
    public ResponseEntity<ExportCreateResponse> createCsv(Authentication auth, @PathVariable UUID bookId) {
        log.info("CREATE CSV export: bookId={}", bookId);
        try {
            UserEntity user = userService.getOrCreate(
                    auth,
                    CurrentUser.subject().orElse(LOCAL),
                    CurrentUser.email().orElse(null)
            );
            log.debug("User resolved: userId={}", user.getId());

            BookEntity book = bookService.requireOwned(bookId, user);
            log.debug("Book verified: bookId={}", book.getId());

            ExportJobEntity job = new ExportJobEntity();
            job.setBook(book);
            job.setRequestedBy(user);
            job.setStatus(ExportStatus.PENDING);

            job = exportRepo.save(job);
            log.info("Export job created: exportId={}, bookId={}, status={}", job.getId(), bookId, job.getStatus());

            return ResponseEntity.accepted().body(new ExportCreateResponse(job.getId(), job.getStatus().name()));
        } catch (Exception e) {
            log.error("Error creating CSV export: bookId={}", bookId, e);
            throw e;
        }
    }

    @GetMapping("/{exportId}")
    public ResponseEntity<ExportResponse> get(
            Authentication auth,
            @PathVariable UUID bookId,
            @PathVariable UUID exportId
    ) {
        log.info("GET export: exportId={}, bookId={}", exportId, bookId);

        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        log.debug("User resolved: userId={}", user.getId());

        // Enforce ownership via book check
        BookEntity book = bookService.requireOwned(bookId, user);
        log.debug("Book verified: bookId={}", book.getId());

        ExportJobEntity job = exportRepo.findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Export not found"
                ));

        return ResponseEntity.ok(toResponse(job));
    }

    @GetMapping("/{exportId}/download")
    public ResponseEntity<ExportDownloadResponse> download(Authentication auth, @PathVariable UUID bookId, @PathVariable UUID exportId) {
        UserEntity user = userService.getOrCreate(
                auth,
                CurrentUser.subject().orElse(LOCAL),
                CurrentUser.email().orElse(null)
        );
        bookService.requireOwned(bookId, user);

        ExportDownloadResponse resp = exportService.getDownloadUrl(bookId, exportId);
        return ResponseEntity.ok(resp);

    }


    private ExportResponse toResponse(ExportJobEntity job) {
        return new ExportResponse(
                job.getId(),
                job.getBook().getId(),
                job.getStatus().name(),
                job.getFileName(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getErrorMessage()
        );
    }
}
