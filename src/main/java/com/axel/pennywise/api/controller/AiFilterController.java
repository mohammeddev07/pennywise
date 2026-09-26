package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.ai.FilterProposalRequest;
import com.axel.pennywise.api.dto.ai.FilterProposalResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.transaction.query.ai.FilterProposalService;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Describe your filter": proposes a P1 filter from a natural-language question. This never applies
 * anything - the client reviews/edits the proposal in the normal advanced filter builder and
 * Applies it through the normal path, exactly like a hand-built filter. Book ownership is resolved
 * first, same as {@link TransactionQueryController}.
 */
@Slf4j
@RestController
@RequestMapping("/v1/books/{bookId}/filter-proposals")
@RequiredArgsConstructor
public class AiFilterController {

  private static final String LOCAL = "local";

  private final UserService userService;
  private final BookService bookService;
  private final FilterProposalService filterProposalService;

  @PostMapping
  public ResponseEntity<FilterProposalResponse> propose(
      Authentication auth,
      @PathVariable UUID bookId,
      @Valid @RequestBody FilterProposalRequest req) {
    UserEntity user =
        userService.getOrCreate(
            auth, CurrentUser.subject().orElse(LOCAL), CurrentUser.email().orElse(null));
    BookEntity book = bookService.requireOwned(bookId, user);

    FilterProposalResponse response = filterProposalService.propose(book, user, req.text());
    log.info("AI filter proposal: bookId={}, status={}", bookId, response.status());
    return ResponseEntity.ok(response);
  }
}
