package com.axel.pennywise.api.dto.summary;

import java.util.List;
import java.util.UUID;

public record MonthlySummaryResponse(
    UUID bookId,
    String month,
    String currencyCode,
    long incomeTotalMinor,
    long expenseTotalMinor,
    List<CategoryBreakdownItem> byCategory) {}
