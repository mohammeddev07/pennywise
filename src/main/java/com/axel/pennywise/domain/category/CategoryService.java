package com.axel.pennywise.domain.category;

import com.axel.pennywise.domain.book.BookEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository repo;

    // Phase 1: This is the correct home for default seeding.
    @Transactional
    public void seedDefaultCategories(BookEntity book) {
        log.debug("Seeding default categories for book: bookId={}", book.getId());

        // Only seed if NO categories exist for this book (any rows at all).
        // This prevents duplicates forever, even if someone deletes/soft-deletes.
        if (repo.existsByBook_Id(book.getId())) {
            log.info("Skipping default category seeding (already exists): bookId={}", book.getId());
            return;
        }

        List<DefaultCategory> expense = List.of(
                new DefaultCategory("Food", "utensils", "#EF4444"),
                new DefaultCategory("Transport", "car", "#F97316"),
                new DefaultCategory("Bills", "receipt", "#EAB308"),
                new DefaultCategory("Rent", "home", "#84CC16"),
                new DefaultCategory("Shopping", "shopping-bag", "#22C55E"),
                new DefaultCategory("Health", "heart-pulse", "#14B8A6"),
                new DefaultCategory("Education", "graduation-cap", "#3B82F6"),
                new DefaultCategory("Entertainment", "popcorn", "#8B5CF6"),
                new DefaultCategory("Other", "circle-ellipsis", "#64748B")
        );
        List<DefaultCategory> income = List.of(
                new DefaultCategory("Salary", "briefcase-business", "#16A34A"),
                new DefaultCategory("Business", "building-2", "#0891B2"),
                new DefaultCategory("Gift", "gift", "#DB2777"),
                new DefaultCategory("Refund", "rotate-ccw", "#2563EB"),
                new DefaultCategory("Other", "circle-ellipsis", "#64748B")
        );

        for (DefaultCategory category : expense) {
            createDefault(book, CategoryType.EXPENSE, category);
        }
        for (DefaultCategory category : income) {
            createDefault(book, CategoryType.INCOME, category);
        }

        log.info("Default categories seeded for book: bookId={}, expenseCount={}, incomeCount={}",
                book.getId(), expense.size(), income.size());
    }

    private void createDefault(BookEntity book, CategoryType type, DefaultCategory defaultCategory) {
        log.debug("Creating default category: bookId={}, type={}, name={}", book.getId(), type, defaultCategory.name());

        CategoryEntity c = new CategoryEntity();
        c.setBook(book);
        c.setType(type);
        c.setName(defaultCategory.name());
        c.setDisabled(false);
        c.setIcon(defaultCategory.icon());
        c.setColor(defaultCategory.color());

        CategoryEntity saved = repo.save(c);
        log.debug("Default category created: categoryId={}, type={}, name={}", saved.getId(), type, defaultCategory.name());
    }

    private record DefaultCategory(String name, String icon, String color) {}
}
