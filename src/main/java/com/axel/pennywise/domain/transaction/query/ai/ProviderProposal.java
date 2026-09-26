package com.axel.pennywise.domain.transaction.query.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Shape of the JSON Gemini returns under {@code responseSchema} (see {@link
 * GeminiFilterClient#buildResponseSchema()}). This is NOT the P1 filter AST - {@link
 * FilterProposalMapper} maps it into that, running it through the same {@link
 * com.axel.pennywise.domain.transaction.query.QueryRequestParser} every other filter goes through.
 * Unknown properties are ignored rather than rejected: the schema already constrains what the model
 * can emit, so this binding is a convenience, not the trust boundary.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ProviderProposal(
    String status,
    String clarification,
    String limitation,
    String topOp,
    List<ProviderGroup> groups,
    String sortField,
    String sortDirection) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ProviderGroup(String op, List<ProviderCondition> conditions) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ProviderCondition(
      String field,
      String operator,
      String stringValue,
      Double numberValue,
      String dateValue,
      String datePreset,
      List<String> stringArrayValue,
      List<Double> numberArrayValue) {}
}
