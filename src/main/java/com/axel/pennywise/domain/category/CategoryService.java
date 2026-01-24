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

        List<String> expense = List.of("Food","Transport","Bills","Rent","Shopping","Health","Education","Entertainment","Other");
        List<String> income = List.of("Salary","Business","Gift","Refund","Other");

        for (String name : expense) {
            createDefault(book, CategoryType.EXPENSE, name);
        }
        for (String name : income) {
            createDefault(book, CategoryType.INCOME, name);
        }

        log.info("Default categories seeded for book: bookId={}, expenseCount={}, incomeCount={}",
                book.getId(), expense.size(), income.size());
    }

    private void createDefault(BookEntity book, CategoryType type, String name) {
        log.debug("Creating default category: bookId={}, type={}, name={}", book.getId(), type, name);

        CategoryEntity c = new CategoryEntity();
        c.setBook(book);
        c.setType(type);
        c.setName(name);
        c.setDisabled(false);

        CategoryEntity saved = repo.save(c);
        log.debug("Default category created: categoryId={}, type={}, name={}", saved.getId(), type, name);
    }
}
