package com.axel.pennywise.api.dto.ai;

import java.util.List;
import java.util.Map;

/**
 * {@code status}: PROPOSAL (filter/sort/summary set), CLARIFY (clarification set, ask the user to
 * rephrase), or UNSUPPORTED (limitation set, no filter). {@code filter} is the same canonical shape
 * as {@code TransactionAnalysisResponse.effectiveFilter} - the mobile client's existing filter
 * builder can render it directly. This is a proposal only: nothing is applied server-side, and it
 * replaces (not merges with) whatever filter the client currently has applied once the user hits
 * Apply.
 */
public record FilterProposalResponse(
    String status,
    Map<String, Object> filter,
    List<Map<String, String>> sort,
    String summary,
    String clarification,
    String limitation) {}
