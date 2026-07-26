package com.axel.pennywise.domain.book;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.axel.pennywise.domain.category.CategoryService;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.exception.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

  @Mock private BookRepository repo;
  @Mock private CategoryService categoryService;

  @InjectMocks private BookService bookService;

  private UserEntity user;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    user = new UserEntity();
    user.setId(userId);
  }

  @Test
  void list_returnsBooksFromRepo() {
    BookEntity b1 = new BookEntity();
    b1.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    BookEntity b2 = new BookEntity();
    b2.setId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

    when(repo.findAllByOwner_IdAndDeletedAtIsNull(userId)).thenReturn(List.of(b1, b2));

    List<BookEntity> result = bookService.list(user);

    assertEquals(2, result.size());
    assertSame(b1, result.get(0));
    assertSame(b2, result.get(1));

    verify(repo).findAllByOwner_IdAndDeletedAtIsNull(userId);
    verifyNoMoreInteractions(repo, categoryService);
  }

  @Test
  void create_savesBookAndSeedsDefaultCategories() {
    // repo.save returns a "saved" entity (often with an id)
    BookEntity saved = new BookEntity();
    saved.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    when(repo.save(any(BookEntity.class))).thenReturn(saved);

    BookEntity result = bookService.create(user, "My Book", "USD", "UTC", 5000L);

    assertSame(saved, result);

    // Verify what we saved (lightweight assertions)
    verify(repo)
        .save(
            argThat(
                b ->
                    b.getOwner() == user
                        && "My Book".equals(b.getName())
                        && "USD".equals(b.getCurrencyCode())
                        && "UTC".equals(b.getTimezone())
                        && Long.valueOf(5000L).equals(b.getOpeningBalanceMinor())));

    verify(categoryService).seedDefaults(saved);
    verifyNoMoreInteractions(repo, categoryService);
  }

  @Test
  void create_rejectsInvalidTimezone() {
    ApiException ex =
        assertThrows(
            ApiException.class,
            () -> bookService.create(user, "My Book", "USD", "Not/A_Timezone", 5000L));

    assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    assertEquals("Invalid book timezone", ex.getMessage());
    verifyNoInteractions(repo, categoryService);
  }

  @Test
  void updateName_setsNameAndSaves() {
    BookEntity existing = new BookEntity();
    existing.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    existing.setName("Old");

    when(repo.save(existing)).thenReturn(existing);

    BookEntity result = bookService.updateName(existing, "New Name");

    assertSame(existing, result);
    assertEquals("New Name", existing.getName());

    verify(repo).save(existing);
    verifyNoMoreInteractions(repo, categoryService);
  }

  @Test
  void requireOwned_returnsBookWhenFound() {
    UUID bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    BookEntity found = new BookEntity();
    found.setId(bookId);

    when(repo.findByIdAndOwner_IdAndDeletedAtIsNull(bookId, userId)).thenReturn(Optional.of(found));

    BookEntity result = bookService.requireOwned(bookId, user);

    assertSame(found, result);

    verify(repo).findByIdAndOwner_IdAndDeletedAtIsNull(bookId, userId);
    verifyNoMoreInteractions(repo, categoryService);
  }

  @Test
  void requireOwned_throwsApiExceptionWhenNotFound() {
    UUID bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    when(repo.findByIdAndOwner_IdAndDeletedAtIsNull(bookId, userId)).thenReturn(Optional.empty());

    ApiException ex =
        assertThrows(ApiException.class, () -> bookService.requireOwned(bookId, user));

    // Minimal assertion without depending on ApiException internals too much
    assertEquals("Book not found", ex.getMessage());

    verify(repo).findByIdAndOwner_IdAndDeletedAtIsNull(bookId, userId);
    verifyNoMoreInteractions(repo, categoryService);
  }
}
