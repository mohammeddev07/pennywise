package com.axel.pennywise.domain.transaction;

record RawTransactionRow(
    int rowNumber,
    String date,
    String time,
    String description,
    String amount,
    String type,
    String category,
    String paymentMethod,
    String notes,
    String currency,
    String externalId) {

  boolean isBlank() {
    return isBlank(date)
        && isBlank(time)
        && isBlank(description)
        && isBlank(amount)
        && isBlank(type)
        && isBlank(category)
        && isBlank(paymentMethod)
        && isBlank(notes)
        && isBlank(currency)
        && isBlank(externalId);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
