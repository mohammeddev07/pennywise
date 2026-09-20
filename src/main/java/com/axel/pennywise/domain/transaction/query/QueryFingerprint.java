package com.axel.pennywise.domain.transaction.query;

import com.axel.pennywise.util.Hashing;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SHA-256 of the canonical filter (+ applied sort). Two requests with the same normalized
 * expression get the same fingerprint, so clients can tell when a cached page/aggregate belongs to
 * a different query. The page is deliberately not part of it.
 */
public final class QueryFingerprint {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private QueryFingerprint() {}

  public static String of(FilterNode filter, List<QuerySpec.SortKey> sortOrNull) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("filter", filter.canonical());
    if (sortOrNull != null) m.put("sort", new QuerySpec.Query(filter, sortOrNull).sortJson());
    try {
      return Hashing.sha256(MAPPER.writeValueAsString(m));
    } catch (Exception e) {
      throw new IllegalStateException("Fingerprint failed", e);
    }
  }
}
