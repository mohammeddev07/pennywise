package com.axel.pennywise.domain.transaction.query.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.transaction.query.FilterNode;
import com.axel.pennywise.domain.transaction.query.FilterNode.Condition;
import com.axel.pennywise.domain.transaction.query.FilterNode.Group;
import com.axel.pennywise.domain.transaction.query.QueryRequestParser;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Query;
import com.axel.pennywise.domain.transaction.query.TxField;
import com.axel.pennywise.domain.transaction.query.ai.ProviderProposal.ProviderCondition;
import com.axel.pennywise.domain.transaction.query.ai.ProviderProposal.ProviderGroup;
import com.axel.pennywise.exception.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FilterProposalMapperTest {

  private final FilterProposalMapper mapper = new FilterProposalMapper(new QueryRequestParser());

  private static BookEntity book(String currency) {
    BookEntity b = new BookEntity();
    b.setCurrencyCode(currency);
    b.setTimezone("UTC");
    return b;
  }

  /** A lone top-level group is passed through as-is (no double-wrapping), so unwrap one level. */
  private static Condition firstCondition(FilterNode node) {
    return (Condition) ((Group) node).children().get(0);
  }

  private static ProviderCondition condition(
      String field, String op, String stringValue, Double numberValue, List<String> arr, List<Double> numArr) {
    return new ProviderCondition(field, op, stringValue, numberValue, null, null, arr, numArr);
  }

  @Test
  void simpleEqualityConditionBuildsValidQuery() {
    ProviderProposal proposal =
        new ProviderProposal(
            "PROPOSAL",
            null,
            null,
            "AND",
            List.of(new ProviderGroup("AND", List.of(condition("type", "EQ", "EXPENSE", null, null, null)))),
            null,
            null);

    Query query = mapper.buildValidatedQuery(proposal, book("USD"), Set.of(), LocalDate.of(2026, 6, 15));

    Condition c = firstCondition(query.filter());
    assertEquals(TxField.TYPE, c.field());
  }

  @Test
  void categoryIdNotOwnedIsRejected() {
    UUID owned = UUID.randomUUID();
    UUID foreign = UUID.randomUUID();
    ProviderProposal proposal =
        new ProviderProposal(
            "PROPOSAL",
            null,
            null,
            "AND",
            List.of(
                new ProviderGroup(
                    "AND", List.of(condition("categoryId", "EQ", foreign.toString(), null, null, null)))),
            null,
            null);

    ApiException e =
        assertThrows(
            ApiException.class,
            () ->
                mapper.buildValidatedQuery(proposal, book("USD"), Set.of(owned), LocalDate.of(2026, 6, 15)));
    assertEquals("AI_FILTER_INVALID_CATEGORY", e.code());
  }

  @Test
  void disallowedFieldIsRejected() {
    ProviderProposal proposal =
        new ProviderProposal(
            "PROPOSAL",
            null,
            null,
            "AND",
            List.of(new ProviderGroup("AND", List.of(condition("id", "EQ", UUID.randomUUID().toString(), null, null, null)))),
            null,
            null);

    ApiException e =
        assertThrows(
            ApiException.class,
            () -> mapper.buildValidatedQuery(proposal, book("USD"), Set.of(), LocalDate.of(2026, 6, 15)));
    assertEquals("AI_FILTER_INVALID", e.code());
  }

  @Test
  void amountBetweenConvertsToMinorUnitsForTwoDecimalCurrency() {
    ProviderProposal proposal =
        new ProviderProposal(
            "PROPOSAL",
            null,
            null,
            "AND",
            List.of(
                new ProviderGroup(
                    "AND",
                    List.of(condition("amountMinor", "BETWEEN", null, null, null, List.of(10.0, 25.5))))),
            null,
            null);

    Query query = mapper.buildValidatedQuery(proposal, book("USD"), Set.of(), LocalDate.of(2026, 6, 15));
    Condition c = firstCondition(query.filter());
    assertEquals(List.of(1000L, 2550L), c.value());
  }

  @Test
  void amountConvertsToMinorUnitsForZeroDecimalCurrency() {
    ProviderProposal proposal =
        new ProviderProposal(
            "PROPOSAL",
            null,
            null,
            "AND",
            List.of(new ProviderGroup("AND", List.of(condition("amountMinor", "EQ", null, 500.0, null, null)))),
            null,
            null);

    Query query = mapper.buildValidatedQuery(proposal, book("JPY"), Set.of(), LocalDate.of(2026, 6, 15));
    Condition c = firstCondition(query.filter());
    assertEquals(500L, c.value());
  }

  @Test
  void datePresetLastMonthResolvesToCalendarMonthBoundaries() {
    ProviderCondition cond =
        new ProviderCondition("occurredOn", "BETWEEN", null, null, null, "LAST_MONTH", null, null);
    ProviderProposal proposal =
        new ProviderProposal(
            "PROPOSAL", null, null, "AND", List.of(new ProviderGroup("AND", List.of(cond))), null, null);

    // Reference date March 15 2026 -> last month is all of February 2026.
    Query query = mapper.buildValidatedQuery(proposal, book("USD"), Set.of(), LocalDate.of(2026, 3, 15));
    Condition c = firstCondition(query.filter());
    assertEquals(List.of(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)), c.value());
  }

  @Test
  void orGroupWithExclusionOperator() {
    ProviderProposal proposal =
        new ProviderProposal(
            "PROPOSAL",
            null,
            null,
            "AND",
            List.of(
                new ProviderGroup(
                    "OR",
                    List.of(
                        condition("paymentMethod", "NOT_IN", null, null, List.of("CASH", "CARD"), null)))),
            null,
            null);

    Query query = mapper.buildValidatedQuery(proposal, book("USD"), Set.of(), LocalDate.of(2026, 6, 15));
    Group g = (Group) query.filter();
    assertEquals(FilterNode.GroupOp.OR, g.op());
  }

  @Test
  void emptyGroupsAreRejected() {
    ProviderProposal proposal = new ProviderProposal("PROPOSAL", null, null, "AND", List.of(), null, null);
    ApiException e =
        assertThrows(
            ApiException.class,
            () -> mapper.buildValidatedQuery(proposal, book("USD"), Set.of(), LocalDate.of(2026, 6, 15)));
    assertEquals("AI_FILTER_INVALID", e.code());
  }

  @Test
  void unknownOperatorIsRejected() {
    ProviderProposal proposal =
        new ProviderProposal(
            "PROPOSAL",
            null,
            null,
            "AND",
            List.of(new ProviderGroup("AND", List.of(condition("type", "MATCHES", "EXPENSE", null, null, null)))),
            null,
            null);
    ApiException e =
        assertThrows(
            ApiException.class,
            () -> mapper.buildValidatedQuery(proposal, book("USD"), Set.of(), LocalDate.of(2026, 6, 15)));
    assertTrue(e.code().startsWith("AI_FILTER_INVALID"));
  }
}
