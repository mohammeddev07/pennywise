package com.axel.pennywise.domain.book;

import com.axel.pennywise.domain.category.CategoryService;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.exception.ApiException;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookService {
  private final BookRepository repo;
  private final CategoryService categoryService;

  public List<BookEntity> list(UserEntity user) {
    log.debug("Listing books for user: userId={}", user.getId());
    List<BookEntity> books = repo.findAllByOwner_IdAndDeletedAtIsNull(user.getId());
    log.debug("Books found: userId={}, count={}", user.getId(), books.size());
    return books;
  }

  @Transactional
  public BookEntity create(
      UserEntity user,
      String name,
      String currencyCode,
      String timezone,
      long openingBalanceMinor) {
    log.debug(
        "Creating book for user: userId={}, name={}, currency={}",
        user.getId(),
        name,
        currencyCode);

    validateTimezone(timezone);

    BookEntity b = new BookEntity();
    b.setOwner(user);
    b.setName(name);
    b.setCurrencyCode(currencyCode);
    b.setTimezone(timezone);
    b.setOpeningBalanceMinor(openingBalanceMinor);

    BookEntity saved = repo.save(b);
    log.info("Book saved: bookId={}, userId={}, name={}", saved.getId(), user.getId(), name);

    log.debug("Seeding default categories for book: bookId={}", saved.getId());
    categoryService.seedDefaults(saved);
    log.debug("Default categories seeded: bookId={}", saved.getId());

    return saved;
  }

  private void validateTimezone(String timezone) {
    try {
      ZoneId.of(timezone);
    } catch (DateTimeException | NullPointerException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid book timezone");
    }
  }

  @Transactional
  public BookEntity updateName(BookEntity book, String newName) {
    book.setName(newName);
    return repo.save(book);
  }

  @Transactional
  public void softDelete(BookEntity book) {
    if (book.getDeletedAt() == null) {
      book.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }
    repo.save(book);
  }

  public BookEntity requireOwned(UUID bookId, UserEntity user) {
    log.debug("Verifying book ownership: bookId={}, userId={}", bookId, user.getId());
    return repo.findByIdAndOwner_IdAndDeletedAtIsNull(bookId, user.getId())
        .orElseThrow(
            () -> {
              log.warn(
                  "Book not found or not owned by user: bookId={}, userId={}",
                  bookId,
                  user.getId());
              return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Book not found");
            });
  }
}
