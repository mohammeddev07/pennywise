package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.transaction.PaymentMethod;
import com.axel.pennywise.domain.transaction.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionUpdateRequest(
    TransactionType type,
    @Min(1) Long amountMinor,
    LocalDate occurredOn,
    UUID categoryId,
    @Size(max = 280) String note,
    @Size(max = 120) String title,
    PaymentMethod paymentMethod,
    OffsetDateTime occurredAt) {
  public TransactionUpdateRequest(
      TransactionType type, Long amountMinor, LocalDate occurredOn, UUID categoryId, String note) {
    this(type, amountMinor, occurredOn, categoryId, note, null, null, null);
  }
}
