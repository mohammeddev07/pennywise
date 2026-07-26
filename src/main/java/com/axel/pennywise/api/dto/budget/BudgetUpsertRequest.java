package com.axel.pennywise.api.dto.budget;

import jakarta.validation.constraints.Min;

public record BudgetUpsertRequest(@Min(0) long amountMinor) {}
