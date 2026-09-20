package com.axel.pennywise.domain.transaction.query;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed, validated request pieces shared by search, analyze and query export. */
public final class QuerySpec {

  private QuerySpec() {}

  public record SortKey(TxField field, boolean asc) {}

  /** {@code sort} always ends with a tie-breaker on {@code id}, so page order is deterministic. */
  public record Query(FilterNode filter, List<SortKey> sort) {

    /** Sort echoed back to the client: what was actually applied, tie-breaker included. */
    public List<Map<String, String>> sortJson() {
      List<Map<String, String>> out = new ArrayList<>();
      for (SortKey k : sort) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("field", k.field().wireName());
        m.put("direction", k.asc() ? "ASC" : "DESC");
        out.add(m);
      }
      return out;
    }
  }

  public record Page(int offset, int limit) {}

  public record Search(Query query, Page page) {}

  public enum Bucket {
    DAY,
    MONTH,
    YEAR
  }

  public record Window(LocalDate startDate, LocalDate endDate) {}

  public record Analyze(FilterNode filter, Bucket bucket, Window window) {}
}
