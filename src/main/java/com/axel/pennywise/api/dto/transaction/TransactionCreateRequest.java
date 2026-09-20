package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.common.MoneyLimits;
import com.axel.pennywise.domain.transaction.PaymentMethod;
import com.axel.pennywise.domain.transaction.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Create body. Audit fields ({@code id}, {@code createdAt}, {@code updatedAt}, {@code version},
 * {@code externalId}, ...) are server-controlled: any unknown property is rejected at
 * deserialization. {@code occurredOn} is the ledger date in the book timezone; {@code occurredAt}
 * is the event instant. Either may be supplied alone (the other is derived in the book timezone);
 * when both are supplied they must agree.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record TransactionCreateRequest(
    @NotNull TransactionType type,
    @Min(1) @Max(MoneyLimits.MAX_TRANSACTION_AMOUNT_MINOR) long amountMinor,
    LocalDate occurredOn,
    @NotNull UUID categoryId,
    @Size(max = 280) String note,
    @Size(max = 120) String title,
    PaymentMethod paymentMethod,
    OffsetDateTime occurredAt) {
  public TransactionCreateRequest(
      TransactionType type, long amountMinor, LocalDate occurredOn, UUID categoryId, String note) {
    this(type, amountMinor, occurredOn, categoryId, note, null, null, null);
  }
}
