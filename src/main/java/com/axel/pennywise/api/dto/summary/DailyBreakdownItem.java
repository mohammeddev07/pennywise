package com.axel.pennywise.api.dto.summary;

import java.time.LocalDate;

public record DailyBreakdownItem(LocalDate date, long incomeTotalMinor, long expenseTotalMinor) {}
