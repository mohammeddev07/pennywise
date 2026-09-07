package com.axel.pennywise.domain.category;

import com.axel.pennywise.domain.book.BookEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {
  private final CategoryRepository repo;

  @Transactional
  public void seedDefaults(BookEntity book) {
    log.debug("Seeding default categories for book: bookId={}", book.getId());

    // Only seed if NO categories exist for this book (any rows at all).
    // This prevents duplicates forever, even if someone deletes/soft-deletes.
    if (repo.existsByBook_Id(book.getId())) {
      log.info("Skipping default category seeding (already exists): bookId={}", book.getId());
      return;
    }

    List<String[]> seeds =
        List.of(
            new String[] {"EXPENSE", "Food", "fast-food-outline", "#FFB020"},
            new String[] {"EXPENSE", "Groceries", "basket-outline", "#34D399"},
            new String[] {"EXPENSE", "Transport", "car-outline", "#60A5FA"},
            new String[] {"EXPENSE", "Rent", "home-outline", "#A78BFA"},
            new String[] {"EXPENSE", "Shopping", "cart-outline", "#F472B6"},
            new String[] {"EXPENSE", "Utilities", "flash-outline", "#FB923C"},
            new String[] {"INCOME", "Salary", "cash-outline", "#22C55E"},
            new String[] {"INCOME", "Freelance", "laptop-outline", "#06B6D4"});

    List<CategoryEntity> entities = seeds.stream().map(seed -> createDefault(book, seed)).toList();

    repo.saveAll(entities);

    log.info(
        "Default categories seeded for book: bookId={}, count={}", book.getId(), entities.size());
  }

  public record CategoryLookupResult(CategoryEntity category, boolean created) {}

  @Transactional
  public CategoryLookupResult getOrCreateForImport(
      BookEntity book, CategoryType type, String rawName) {
    String normalized = rawName.trim().replaceAll("\\s+", " ");
    return repo.findByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(
            book.getId(), type, normalized)
        .map(existing -> new CategoryLookupResult(existing, false))
        .orElseGet(() -> new CategoryLookupResult(createForImport(book, type, normalized), true));
  }

  private CategoryEntity createForImport(BookEntity book, CategoryType type, String name) {
    CategoryEntity c = new CategoryEntity();
    c.setBook(book);
    c.setType(type);
    c.setName(name);
    c.setDisabled(false);
    CategoryEntity saved = repo.save(c);
    log.info(
        "Category auto-created during import: categoryId={}, bookId={}, type={}, name={}",
        saved.getId(),
        book.getId(),
        type,
        name);
    return saved;
  }

  private CategoryEntity createDefault(BookEntity book, String[] seed) {
    CategoryType type = CategoryType.valueOf(seed[0]);
    log.debug(
        "Creating default category: bookId={}, type={}, name={}", book.getId(), type, seed[1]);

    CategoryEntity c = new CategoryEntity();
    c.setBook(book);
    c.setType(type);
    c.setName(seed[1]);
    c.setIcon(seed[2]);
    c.setColor(seed[3]);
    c.setDisabled(false);
    return c;
  }
}
