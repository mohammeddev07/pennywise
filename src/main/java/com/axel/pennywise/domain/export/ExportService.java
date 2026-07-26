package com.axel.pennywise.domain.export;

import com.axel.pennywise.api.dto.export.ExportDownloadResponse;
import com.axel.pennywise.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExportService {

  private final ExportJobRepository exportRepo;
  private final ExportPresignService presignService;

  @Transactional(readOnly = true)
  public ExportDownloadResponse getDownloadUrl(UUID bookId, UUID exportId) {
    ExportJobEntity job =
        exportRepo
            .findByIdAndBook_IdAndDeletedAtIsNull(exportId, bookId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Export not found"));

    if (job.getStatus() != ExportStatus.COMPLETED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "EXPORT_NOT_READY",
          "Export is not ready for download",
          List.of(
              Map.of(
                  "exportId",
                  exportId.toString(),
                  "status",
                  job.getStatus() == null ? "null" : job.getStatus().name())));
    }

    // When async completion is implemented, this should exist.
    if (job.getStorageKey() == null || job.getStorageKey().isBlank()) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "INTERNAL_ERROR",
          "Export is COMPLETED but storageKey is missing");
    }

    String url = presignService.presign(job.getStorageKey());
    return new ExportDownloadResponse(url);
  }
}
