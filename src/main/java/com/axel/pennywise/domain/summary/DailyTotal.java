package com.axel.pennywise.domain.summary;

import java.time.LocalDate;

public record DailyTotal(LocalDate occurredOn, long incomeTotalMinor, long expenseTotalMinor) {}
