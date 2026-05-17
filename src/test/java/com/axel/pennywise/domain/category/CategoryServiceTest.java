package com.axel.pennywise.domain.category;

import com.axel.pennywise.domain.book.BookEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository repo;

    @InjectMocks private CategoryService categoryService;

    private BookEntity book;
    private UUID bookId;

    @BeforeEach
    void setUp() {
        bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        book = new BookEntity();
        book.setId(bookId);
    }

    @Test
    void seedDefaults_skipsWhenAnyCategoryExists() {
        when(repo.existsByBook_Id(bookId)).thenReturn(true);

        categoryService.seedDefaults(book);

        verify(repo).existsByBook_Id(bookId);
        verify(repo, never()).save(any(CategoryEntity.class));
        verify(repo, never()).saveAll(any());
        verifyNoMoreInteractions(repo);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void seedDefaults_createsDefaultsWhenNoneExist() {
        when(repo.existsByBook_Id(bookId)).thenReturn(false);

        categoryService.seedDefaults(book);

        // Capture all CategoryEntity instances passed to saveAll()
        ArgumentCaptor<Iterable<CategoryEntity>> captor = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(repo).existsByBook_Id(bookId);
        verify(repo).saveAll(captor.capture());
        verifyNoMoreInteractions(repo);

        List<CategoryEntity> saved = new ArrayList<>();
        captor.getValue().forEach(saved::add);
        assertEquals(8, saved.size());

        // Validate each saved category has correct book + disabled=false + non-empty name + type
        for (CategoryEntity c : saved) {
            assertSame(book, c.getBook());
            assertNotNull(c.getType());
            assertNotNull(c.getName());
            assertFalse(c.getName().isBlank());
            assertFalse(c.isDisabled());
        }

        // Validate counts by type
        long expenseCount = saved.stream().filter(c -> c.getType() == CategoryType.EXPENSE).count();
        long incomeCount = saved.stream().filter(c -> c.getType() == CategoryType.INCOME).count();
        assertEquals(6, expenseCount);
        assertEquals(2, incomeCount);

        assertSeed(saved, CategoryType.EXPENSE, "Food", "fast-food-outline", "#FFB020");
        assertSeed(saved, CategoryType.EXPENSE, "Groceries", "basket-outline", "#34D399");
        assertSeed(saved, CategoryType.EXPENSE, "Transport", "car-outline", "#60A5FA");
        assertSeed(saved, CategoryType.EXPENSE, "Rent", "home-outline", "#A78BFA");
        assertSeed(saved, CategoryType.EXPENSE, "Shopping", "cart-outline", "#F472B6");
        assertSeed(saved, CategoryType.EXPENSE, "Utilities", "flash-outline", "#FB923C");
        assertSeed(saved, CategoryType.INCOME, "Salary", "cash-outline", "#22C55E");
        assertSeed(saved, CategoryType.INCOME, "Freelance", "laptop-outline", "#06B6D4");
    }

    private void assertSeed(List<CategoryEntity> saved, CategoryType type, String name, String icon, String color) {
        assertTrue(saved.stream().anyMatch(c ->
                        c.getType() == type &&
                                name.equals(c.getName()) &&
                                icon.equals(c.getIcon()) &&
                                color.equals(c.getColor())),
                "Missing category seed: " + type + " " + name);
    }
}
