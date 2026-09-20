package com.axel.pennywise.api.dto.transaction;

import java.util.List;

/**
 * {@code queryFingerprint} identifies filter + sort (not the page): when it changes, or after any
 * mutation or refresh, the client must restart from offset 0 because offset paging is not a
 * cross-request snapshot.
 */
public record TransactionSearchResponse(
    List<TransactionResponse> items, long totalCount, PageInfo page, String queryFingerprint) {

  public record PageInfo(int offset, int limit, boolean hasMore) {}
}
