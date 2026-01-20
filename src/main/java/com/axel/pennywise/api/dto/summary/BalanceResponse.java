package com.axel.pennywise.api.dto.summary;

import java.util.UUID;

public record BalanceResponse(UUID bookId, String currencyCode, long balanceMinor) {}
