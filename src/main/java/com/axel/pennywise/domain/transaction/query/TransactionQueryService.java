package com.axel.pennywise.domain.transaction.query;

import com.axel.pennywise.domain.transaction.TransactionEntity;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Page;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Query;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

  public record SearchResult(List<TransactionEntity> items, long totalCount, boolean hasMore) {}

  private final TransactionQueryRepository repo;

  /**
   * Count and page run in one repeatable-read transaction so {@code totalCount}, {@code hasMore}
   * and the items describe the same snapshot. Consecutive requests are still independent snapshots
   * (offset paging), which is why clients reset pagination after mutations.
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SearchResult search(UUID bookId, Query query, Page page) {
    long total = repo.count(bookId, query.filter());
    List<TransactionEntity> items =
        total <= page.offset()
            ? List.of()
            : repo.page(bookId, query.filter(), query.sort(), page.offset(), page.limit());
    return new SearchResult(items, total, (long) page.offset() + items.size() < total);
  }
}
