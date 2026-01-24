package com.axel.pennywise.domain.export;

import com.axel.pennywise.api.dto.export.ExportDownloadResponse;
import com.axel.pennywise.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock private ExportJobRepository exportRepo;
    @Mock private ExportPresignService presignService;

    @InjectMocks private ExportService exportService;

    private UUID bookId;
    private UUID exportId;

    @BeforeEach
    void setUp() {
        bookId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        exportId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    }

    @Test
    void getDownloadUrl_throwsNotFound_whenJobMissing() {
        when(exportRepo.findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> exportService.getDownloadUrl(bookId, exportId));

        assertEquals("Export not found", ex.getMessage());

        verify(exportRepo).findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId);
        verifyNoInteractions(presignService);
        verifyNoMoreInteractions(exportRepo);
    }

    @Test
    void getDownloadUrl_throwsConflict_whenNotCompleted_pending() {
        ExportJobEntity job = new ExportJobEntity();
        job.setId(exportId);
        job.setStatus(ExportStatus.PENDING);

        when(exportRepo.findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId)).thenReturn(Optional.of(job));

        ApiException ex = assertThrows(ApiException.class,
                () -> exportService.getDownloadUrl(bookId, exportId));

        assertEquals("Export is not ready for download", ex.getMessage());

        verify(exportRepo).findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId);
        verifyNoInteractions(presignService);
        verifyNoMoreInteractions(exportRepo);
    }

    @Test
    void getDownloadUrl_throwsConflict_whenNotCompleted_nullStatus() {
        ExportJobEntity job = new ExportJobEntity();
        job.setId(exportId);
        job.setStatus(null);

        when(exportRepo.findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId)).thenReturn(Optional.of(job));

        ApiException ex = assertThrows(ApiException.class,
                () -> exportService.getDownloadUrl(bookId, exportId));

        assertEquals("Export is not ready for download", ex.getMessage());

        verify(exportRepo).findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId);
        verifyNoInteractions(presignService);
        verifyNoMoreInteractions(exportRepo);
    }

    @Test
    void getDownloadUrl_throwsInternalError_whenCompletedButStorageKeyMissing() {
        ExportJobEntity job = new ExportJobEntity();
        job.setId(exportId);
        job.setStatus(ExportStatus.COMPLETED);
        job.setStorageKey(null);

        when(exportRepo.findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId)).thenReturn(Optional.of(job));

        ApiException ex = assertThrows(ApiException.class,
                () -> exportService.getDownloadUrl(bookId, exportId));

        assertEquals("Export is COMPLETED but storageKey is missing", ex.getMessage());

        verify(exportRepo).findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId);
        verifyNoInteractions(presignService);
        verifyNoMoreInteractions(exportRepo);
    }

    @Test
    void getDownloadUrl_throwsInternalError_whenCompletedButStorageKeyBlank() {
        ExportJobEntity job = new ExportJobEntity();
        job.setId(exportId);
        job.setStatus(ExportStatus.COMPLETED);
        job.setStorageKey("   ");

        when(exportRepo.findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId)).thenReturn(Optional.of(job));

        ApiException ex = assertThrows(ApiException.class,
                () -> exportService.getDownloadUrl(bookId, exportId));

        assertEquals("Export is COMPLETED but storageKey is missing", ex.getMessage());

        verify(exportRepo).findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId);
        verifyNoInteractions(presignService);
        verifyNoMoreInteractions(exportRepo);
    }

    @Test
    void getDownloadUrl_returnsPresignedUrl_whenCompletedAndStorageKeyPresent() {
        ExportJobEntity job = new ExportJobEntity();
        job.setId(exportId);
        job.setStatus(ExportStatus.COMPLETED);
        job.setStorageKey("exports/book1/export1.csv");

        when(exportRepo.findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId)).thenReturn(Optional.of(job));
        when(presignService.presign("exports/book1/export1.csv")).thenReturn("https://example.com/presigned-url");

        ExportDownloadResponse resp = exportService.getDownloadUrl(bookId, exportId);

        String url = extractUrl(resp);
        assertEquals("https://example.com/presigned-url", url);

        verify(exportRepo).findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId);
        verify(presignService).presign("exports/book1/export1.csv");
        verifyNoMoreInteractions(exportRepo, presignService);
    }

    // Helper to avoid guessing whether ExportDownloadResponse is a record or a class
    private static String extractUrl(ExportDownloadResponse resp) {
        try {
            // record accessor
            return (String) resp.getClass().getMethod("url").invoke(resp);
        } catch (Exception ignored) {
            // not a record
        }
        try {
            // bean getter
            return (String) resp.getClass().getMethod("getUrl").invoke(resp);
        } catch (Exception e) {
            throw new RuntimeException("Cannot extract url from ExportDownloadResponse; add url()/getUrl() accessor.", e);
        }
    }
}
