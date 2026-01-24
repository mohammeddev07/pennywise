package com.axel.pennywise.api.dto.export;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportResponse(
        UUID id,
        UUID bookId,
        String status,
        String fileName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String errorMessage
) {}
