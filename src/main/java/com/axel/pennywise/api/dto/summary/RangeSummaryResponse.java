package com.axel.pennywise.api.dto.summary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RangeSummaryResponse(
    UUID bookId,
    LocalDate startDate,
    LocalDate endDate,
    String currencyCode,
    long incomeTotalMinor,
    long expenseTotalMinor,
    long transactionCount,
    List<CategoryBreakdownItem> byCategory,
    List<DailyBreakdownItem> byDay) {}
