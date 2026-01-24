package com.axel.pennywise.domain.category;

import com.axel.pennywise.domain.book.BookEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void seedDefaultCategories_skipsWhenAnyCategoryExists() {
        when(repo.existsByBook_Id(bookId)).thenReturn(true);

        categoryService.seedDefaultCategories(book);

        verify(repo).existsByBook_Id(bookId);
        verify(repo, never()).save(any(CategoryEntity.class));
        verifyNoMoreInteractions(repo);
    }

    @Test
    void seedDefaultCategories_createsDefaultsWhenNoneExist() {
        when(repo.existsByBook_Id(bookId)).thenReturn(false);

        // Keep save() simple: return the same entity (or a new one). The service doesn't use the return value.
        when(repo.save(any(CategoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        categoryService.seedDefaultCategories(book);

        // Capture all CategoryEntity instances passed to save()
        ArgumentCaptor<CategoryEntity> captor = ArgumentCaptor.forClass(CategoryEntity.class);
        verify(repo).existsByBook_Id(bookId);
        verify(repo, times(14)).save(captor.capture());
        verifyNoMoreInteractions(repo);

        List<CategoryEntity> saved = captor.getAllValues();
        assertEquals(14, saved.size());

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
        assertEquals(9, expenseCount);
        assertEquals(5, incomeCount);

        // Optional: validate the exact names seeded (tight but still reasonable)
        List<String> expenseNames = List.of("Food","Transport","Bills","Rent","Shopping","Health","Education","Entertainment","Other");
        List<String> incomeNames = List.of("Salary","Business","Gift","Refund","Other");

        for (String name : expenseNames) {
            assertTrue(saved.stream().anyMatch(c -> c.getType() == CategoryType.EXPENSE && name.equals(c.getName())),
                    "Missing expense category: " + name);
        }
        for (String name : incomeNames) {
            assertTrue(saved.stream().anyMatch(c -> c.getType() == CategoryType.INCOME && name.equals(c.getName())),
                    "Missing income category: " + name);
        }
    }
}

