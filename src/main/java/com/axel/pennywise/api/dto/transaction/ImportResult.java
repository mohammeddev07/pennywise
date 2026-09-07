package com.axel.pennywise.api.dto.transaction;

import java.util.List;

public record ImportResult(
    int totalRows,
    int importedCount,
    int skippedBlankCount,
    int skippedDuplicateCount,
    int failedCount,
    List<String> categoriesCreated,
    List<ImportRowError> errors) {}
