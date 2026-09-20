package com.axel.pennywise.api.dto.transaction;

import com.axel.pennywise.domain.common.MoneyLimits;
import com.axel.pennywise.domain.transaction.PaymentMethod;
import com.axel.pennywise.domain.transaction.TransactionType;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * PATCH body with presence tracking. Every property is an {@link Optional} bound directly to a
 * field so Jackson keeps "omitted" and "explicit null" apart: Java {@code null} = property omitted
 * (unchanged), {@code Optional.empty()} = explicit JSON {@code null}, {@code Optional.of(v)} =
 * value. (A record cannot do this: absent creator arguments also arrive as {@code
 * Optional.empty()}.)
 *
 * <ul>
 *   <li>{@code title}, {@code note}, {@code paymentMethod}: explicit null clears the value. A blank
 *       string also clears title/note (service normalization).
 *   <li>{@code type}, {@code amountMinor}, {@code occurredOn}, {@code occurredAt}, {@code
 *       categoryId}: explicit null is rejected by the service with 400 VALIDATION_ERROR.
 *   <li>Any other property ({@code id}, {@code createdAt}, {@code updatedAt}, {@code version},
 *       {@code externalId}, ...) is rejected at deserialization and never reaches this object.
 * </ul>
 */
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(
    JsonInclude.Include.NON_NULL) // serialize: omitted stays omitted, Optional.empty() -> null
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE) // Jackson
@AllArgsConstructor
public final class TransactionUpdateRequest {

  private Optional<TransactionType> type;
  private Optional<@Min(1) @Max(MoneyLimits.MAX_TRANSACTION_AMOUNT_MINOR) Long> amountMinor;
  private Optional<LocalDate> occurredOn;
  private Optional<UUID> categoryId;
  private Optional<@Size(max = 280) String> note;
  private Optional<@Size(max = 120) String> title;
  private Optional<PaymentMethod> paymentMethod;
  private Optional<OffsetDateTime> occurredAt;

  /** Convenience for callers/tests: a plain value is "present", a Java null is "omitted". */
  public TransactionUpdateRequest(
      TransactionType type,
      Long amountMinor,
      LocalDate occurredOn,
      UUID categoryId,
      String note,
      String title,
      PaymentMethod paymentMethod,
      OffsetDateTime occurredAt) {
    this(
        present(type),
        present(amountMinor),
        present(occurredOn),
        present(categoryId),
        present(note),
        present(title),
        present(paymentMethod),
        present(occurredAt));
  }

  public TransactionUpdateRequest(
      TransactionType type, Long amountMinor, LocalDate occurredOn, UUID categoryId, String note) {
    this(type, amountMinor, occurredOn, categoryId, note, null, null, null);
  }

  private static <T> Optional<T> present(T value) {
    return value == null ? null : Optional.of(value);
  }

  /** True when no property was supplied at all (neither a value nor an explicit null). */
  public boolean isEmpty() {
    return type == null
        && amountMinor == null
        && occurredOn == null
        && categoryId == null
        && note == null
        && title == null
        && paymentMethod == null
        && occurredAt == null;
  }
}
