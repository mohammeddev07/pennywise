package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.api.dto.common.CursorPage;

public record TransactionListResponse(CursorPage<TransactionResponse> page) {}
