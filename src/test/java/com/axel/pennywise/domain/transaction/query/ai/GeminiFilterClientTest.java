package com.axel.pennywise.domain.transaction.query.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GeminiFilterClientTest {

  private final GeminiFilterClient client = new GeminiFilterClient(RestClient.builder());

  /**
   * Gemini has been observed to return {@code status: PROPOSAL} with "groups" omitted despite a
   * prose instruction saying not to (see FilterProposalService's system instruction) - a prose
   * instruction is only a suggestion. "required" + "minItems" are enforced by Gemini's structured
   * output, so this is the actual guarantee against an empty proposal, not the prompt text.
   */
  @Test
  @SuppressWarnings("unchecked")
  void schemaRequiresNonEmptyGroupsAndConditions() {
    Map<String, Object> schema = client.buildResponseSchema();

    assertTrue(((List<String>) schema.get("required")).contains("groups"));

    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
    Map<String, Object> groups = (Map<String, Object>) properties.get("groups");
    assertEquals(1, groups.get("minItems"));

    Map<String, Object> group = (Map<String, Object>) groups.get("items");
    assertTrue(((List<String>) group.get("required")).contains("conditions"));
    Map<String, Object> groupProperties = (Map<String, Object>) group.get("properties");
    Map<String, Object> conditions = (Map<String, Object>) groupProperties.get("conditions");
    assertEquals(1, conditions.get("minItems"));
  }
}
