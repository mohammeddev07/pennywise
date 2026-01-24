package com.axel.pennywise.api.controller;

import com.axel.pennywise.api.dto.category.CategoryCreateRequest;
import com.axel.pennywise.api.dto.category.CategoryUpdateRequest;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.category.CategoryType;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;
    @Mock
    private BookService bookService;
    @Mock
    private CategoryRepository categoryRepository;

    private ObjectMapper objectMapper;

    private UserEntity testUser;
    private BookEntity testBook;
    private CategoryEntity testCategory;

    private UUID bookId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();

        CategoryController controller = new CategoryController(userService, bookService, categoryRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        categoryId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        testUser = new UserEntity();
        testUser.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        testBook = new BookEntity();
        testBook.setId(bookId);
        testBook.setName("Test Book");

        testCategory = new CategoryEntity();
        testCategory.setId(categoryId);
        testCategory.setBook(testBook);
        testCategory.setType(CategoryType.EXPENSE);
        testCategory.setName("Food");
        testCategory.setDisabled(false);
        testCategory.setVersion(0L);
        testCategory.setCreatedAt(OffsetDateTime.now());
        testCategory.setUpdatedAt(OffsetDateTime.now());
    }

    private static RequestPostProcessor auth() {
        return request -> {
            request.setUserPrincipal(new TestingAuthenticationToken("test-user", "N/A"));
            return request;
        };
    }

    @Test
    void testListCategories() throws Exception {
        when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
        when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
        when(categoryRepository.findAllByBook_IdAndDeletedAtIsNull(bookId))
                .thenReturn(Collections.singletonList(testCategory));

        mockMvc.perform(get("/v1/books/{bookId}/categories", bookId).with(auth()))
                .andExpect(status().isOk());

        verify(userService).getOrCreate(any(), any(), any());
        verify(bookService).requireOwned(bookId, testUser);
        verify(categoryRepository).findAllByBook_IdAndDeletedAtIsNull(bookId);
        verifyNoMoreInteractions(userService, bookService, categoryRepository);
    }

    @Test
    void testCreateCategory() throws Exception {
        when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
        when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

        // Controller normalizes name; keep stubs aligned with actual call arguments.
        when(categoryRepository.existsByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(
                eq(bookId), eq(CategoryType.INCOME), eq("Salary")
        )).thenReturn(false);

        // Return an entity that matches what you want back
        CategoryEntity saved = new CategoryEntity();
        saved.setId(categoryId);
        saved.setBook(testBook);
        saved.setType(CategoryType.INCOME);
        saved.setName("Salary");
        saved.setDisabled(false);
        saved.setVersion(0L);
        saved.setCreatedAt(OffsetDateTime.now());
        saved.setUpdatedAt(OffsetDateTime.now());

        when(categoryRepository.save(any(CategoryEntity.class))).thenReturn(saved);

        CategoryCreateRequest req = new CategoryCreateRequest(CategoryType.INCOME, "Salary");

        mockMvc.perform(post("/v1/books/{bookId}/categories", bookId).with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verify(userService).getOrCreate(any(), any(), any());
        verify(bookService).requireOwned(bookId, testUser);
        verify(categoryRepository).existsByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(
                bookId, CategoryType.INCOME, "Salary"
        );
        verify(categoryRepository).save(any(CategoryEntity.class));
        verifyNoMoreInteractions(userService, bookService, categoryRepository);
    }

    @Test
    void testCreateCategoryWithBlankName() throws Exception {
        // IMPORTANT: do NOT stub anything.
        // With @Valid, this typically fails before controller invocation.
        CategoryCreateRequest req = new CategoryCreateRequest(CategoryType.EXPENSE, "  ");

        mockMvc.perform(post("/v1/books/{bookId}/categories", bookId).with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, bookService, categoryRepository);
    }

    @Test
    void testUpdateCategory() throws Exception {
        when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
        when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);

        when(categoryRepository.findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId))
                .thenReturn(Optional.of(testCategory));

        // Because "Food" -> "Groceries" triggers the uniqueness check
        when(categoryRepository.existsByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(
                eq(bookId), eq(CategoryType.EXPENSE), eq("Groceries")
        )).thenReturn(false);

        // return the saved entity (optionally bump version so ETag changes)
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(inv -> {
            CategoryEntity c = inv.getArgument(0);
            c.setVersion(1L);
            return c;
        });

        CategoryUpdateRequest req = new CategoryUpdateRequest("Groceries", null);

        mockMvc.perform(patch("/v1/books/{bookId}/categories/{categoryId}", bookId, categoryId)
                        .contentType("application/json")
                        .header("If-Match", "\"0\"")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(userService).getOrCreate(any(), any(), any());
        verify(bookService).requireOwned(bookId, testUser);

        verify(categoryRepository).findByIdAndBook_IdAndDeletedAtIsNull(categoryId, bookId);
        verify(categoryRepository).existsByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(
                bookId, CategoryType.EXPENSE, "Groceries"
        );
        verify(categoryRepository).save(any(CategoryEntity.class));

        verifyNoMoreInteractions(userService, bookService, categoryRepository);
    }


    @Test
    void testUpdateCategoryWithoutIfMatch() throws Exception {
        // IMPORTANT: do NOT stub anything.
        // Missing required header fails before controller invocation.
        CategoryUpdateRequest req = new CategoryUpdateRequest("Updated Name", null);

        mockMvc.perform(patch("/v1/books/{bookId}/categories/{categoryId}", bookId, categoryId).with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, bookService, categoryRepository);
    }
}
