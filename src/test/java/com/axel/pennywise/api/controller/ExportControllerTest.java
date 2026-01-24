package com.axel.pennywise.api.controller;

import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookService;
import com.axel.pennywise.domain.export.ExportJobEntity;
import com.axel.pennywise.domain.export.ExportJobRepository;
import com.axel.pennywise.domain.export.ExportService;
import com.axel.pennywise.domain.export.ExportStatus;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

	private MockMvc mockMvc;

	@Mock private UserService userService;
	@Mock private BookService bookService;
	@Mock private ExportJobRepository exportJobRepository;
	@Mock private ExportService exportService;

    private UserEntity testUser;
	private BookEntity testBook;
	private ExportJobEntity testExport;

	private UUID bookId;
	private UUID exportId;

	@BeforeEach
	void setUp() {
        new ObjectMapper().findAndRegisterModules();

        ExportController controller = new ExportController(userService, bookService, exportJobRepository, exportService);

		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new TestApiExceptionHandler())
				.build();

		bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
		exportId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

		testUser = new UserEntity();
		testUser.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

		testBook = new BookEntity();
		testBook.setId(bookId);

		testExport = new ExportJobEntity();
		testExport.setId(exportId);
		testExport.setBook(testBook);
		testExport.setRequestedBy(testUser);
		testExport.setStatus(ExportStatus.PENDING);
		testExport.setCreatedAt(OffsetDateTime.now());
		testExport.setUpdatedAt(OffsetDateTime.now());
	}

	private static RequestPostProcessor auth() {
		return request -> {
			request.setUserPrincipal(new TestingAuthenticationToken("test-user", "N/A"));
			return request;
		};
	}

	@Test
	void testCreateCsvExport() throws Exception {
		when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
		when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
		when(exportJobRepository.save(any(ExportJobEntity.class))).thenReturn(testExport);

		mockMvc.perform(post("/v1/books/{bookId}/exports/csv", bookId).with(auth()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.exportId").value(exportId.toString()))
				.andExpect(jsonPath("$.status").value("PENDING"));

		verify(userService).getOrCreate(any(), any(), any());
		verify(bookService).requireOwned(bookId, testUser);
		verify(exportJobRepository).save(any(ExportJobEntity.class));
		verifyNoMoreInteractions(userService, bookService, exportJobRepository, exportService);
	}


	@Test
	void testGetExport() throws Exception {
		when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
		when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
		when(exportJobRepository.findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId))
				.thenReturn(Optional.of(testExport));

		mockMvc.perform(get("/v1/books/{bookId}/exports/{exportId}", bookId, exportId).with(auth()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(exportId.toString()))
				.andExpect(jsonPath("$.bookId").value(bookId.toString()))
				.andExpect(jsonPath("$.status").value("PENDING"));

		verify(userService).getOrCreate(any(), any(), any());
		verify(bookService).requireOwned(bookId, testUser);
		verify(exportJobRepository).findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId);
		verifyNoMoreInteractions(userService, bookService, exportJobRepository, exportService);
	}

	@Test
	void testGetExportNotFound() throws Exception {
		UUID randomId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

		when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
		when(bookService.requireOwned(bookId, testUser)).thenReturn(testBook);
		when(exportJobRepository.findByIdAndBook_IdAndDeletedAtIsNull(randomId, bookId))
				.thenReturn(Optional.empty());

		mockMvc.perform(get("/v1/books/{bookId}/exports/{exportId}", bookId, randomId).with(auth()))
				.andExpect(status().isNotFound());

		verify(userService).getOrCreate(any(), any(), any());
		verify(bookService).requireOwned(bookId, testUser);
		verify(exportJobRepository).findByIdAndBook_IdAndDeletedAtIsNull(randomId, bookId);
		verifyNoMoreInteractions(userService, bookService, exportJobRepository, exportService);
	}

	/**
	 * Test-only exception mapping so ApiException becomes an HTTP status in MockMvc standalone tests.
	 */
	@RestControllerAdvice
	static class TestApiExceptionHandler {

		@ExceptionHandler(ApiException.class)
		ResponseEntity<Void> handle(ApiException ex) {
			return ResponseEntity.status(extractStatus(ex)).build();
		}

		private static HttpStatus extractStatus(ApiException ex) {
			for (String m : List.of("getStatus", "status", "getHttpStatus", "httpStatus")) {
				try {
					Method method = ex.getClass().getMethod(m);
					Object v = method.invoke(ex);
					HttpStatus hs = coerce(v);
					if (hs != null) return hs;
				} catch (Exception ignored) {
					// Ignore and try next
				}
			}
			for (String f : List.of("status", "httpStatus")) {
				try {
					Field field = ex.getClass().getDeclaredField(f);
					field.setAccessible(true);
					Object v = field.get(ex);
					HttpStatus hs = coerce(v);
					if (hs != null) return hs;
				} catch (Exception ignored) {
					// Ignore and try next
				}
			}
			return HttpStatus.INTERNAL_SERVER_ERROR;
		}

		private static HttpStatus coerce(Object v) {
			if (v instanceof HttpStatus hs) return hs;
			if (v instanceof HttpStatusCode hsc) return HttpStatus.valueOf(hsc.value());
			if (v instanceof Integer i) return HttpStatus.valueOf(i);
			return null;
		}
	}
}

