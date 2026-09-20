package com.axel.pennywise.api.dto.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axel.pennywise.domain.transaction.PaymentMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * PATCH presence semantics live entirely in Jackson deserialization of the record: omitted vs
 * explicit null must be distinguishable, required fields must refuse null, and audit fields must
 * never be accepted. These checks need no database.
 */
class TransactionUpdateRequestJsonTest {

  private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void emptyObject_hasNoPresentProperties() throws Exception {
    TransactionUpdateRequest req = mapper.readValue("{}", TransactionUpdateRequest.class);
    assertTrue(req.isEmpty());
    assertNull(req.title());
    assertNull(req.note());
    assertNull(req.paymentMethod());
  }

  @Test
  void explicitNull_onNullableField_isPresentAndEmpty() throws Exception {
    TransactionUpdateRequest req =
        mapper.readValue(
            "{\"title\":null,\"note\":null,\"paymentMethod\":null}",
            TransactionUpdateRequest.class);
    assertFalse(req.isEmpty());
    assertEquals(Optional.empty(), req.title());
    assertEquals(Optional.empty(), req.note());
    assertEquals(Optional.empty(), req.paymentMethod());
  }

  @Test
  void value_onNullableField_isPresent() throws Exception {
    TransactionUpdateRequest req =
        mapper.readValue(
            "{\"title\":\"Coffee\",\"paymentMethod\":\"CARD\"}", TransactionUpdateRequest.class);
    assertEquals(Optional.of("Coffee"), req.title());
    assertEquals(Optional.of(PaymentMethod.CARD), req.paymentMethod());
    assertNull(req.note());
  }

  @Test
  void explicitNull_onRequiredField_isPresentAndEmpty_soTheServiceCanRejectIt() throws Exception {
    for (String field :
        new String[] {"type", "amountMinor", "occurredOn", "occurredAt", "categoryId"}) {
      TransactionUpdateRequest req =
          mapper.readValue("{\"" + field + "\":null}", TransactionUpdateRequest.class);
      assertFalse(req.isEmpty(), field);
    }
    assertEquals(
        Optional.empty(),
        mapper.readValue("{\"type\":null}", TransactionUpdateRequest.class).type());
    assertNull(mapper.readValue("{}", TransactionUpdateRequest.class).type());
  }

  @Test
  void auditAndReadOnlyFields_areRejected() {
    for (String field :
        new String[] {
          "id", "bookId", "createdAt", "updatedAt", "deletedAt", "version", "externalId"
        }) {
      String json = "{\"title\":\"x\",\"" + field + "\":\"2026-01-01T00:00:00Z\"}";
      UnrecognizedPropertyException ex =
          assertThrows(
              UnrecognizedPropertyException.class,
              () -> mapper.readValue(json, TransactionUpdateRequest.class),
              field);
      assertEquals(field, ex.getPropertyName());
    }
  }

  @Test
  void createRequest_rejectsAuditFields() {
    String json =
        "{\"type\":\"EXPENSE\",\"amountMinor\":100,\"categoryId\":"
            + "\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\",\"occurredOn\":\"2026-01-01\","
            + "\"createdAt\":\"2020-01-01T00:00:00Z\"}";
    UnrecognizedPropertyException ex =
        assertThrows(
            UnrecognizedPropertyException.class,
            () -> mapper.readValue(json, TransactionCreateRequest.class));
    assertEquals("createdAt", ex.getPropertyName());
  }

  @Test
  void serialization_roundTripsPresence() throws Exception {
    TransactionUpdateRequest req =
        new TransactionUpdateRequest(
            null, null, null, null, null, Optional.empty(), Optional.of(PaymentMethod.CASH), null);
    String json = mapper.writeValueAsString(req);
    assertEquals("{\"title\":null,\"paymentMethod\":\"CASH\"}", json);

    TransactionUpdateRequest back = mapper.readValue(json, TransactionUpdateRequest.class);
    assertEquals(Optional.empty(), back.title());
    assertEquals(Optional.of(PaymentMethod.CASH), back.paymentMethod());
    assertNull(back.note());
  }
}
