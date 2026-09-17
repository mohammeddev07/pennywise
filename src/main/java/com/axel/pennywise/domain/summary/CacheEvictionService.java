package com.axel.pennywise.domain.summary;

import com.axel.pennywise.config.CacheConfig;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts every cached read that can go stale after a transaction/category-name write for a
 * book. Transaction writes and category renames can each touch an unbounded set of months (an
 * edit can move a transaction across a month boundary, an import can span many months), so
 * per-month keys aren't safe to target - this walks each cache and drops every entry whose key
 * is scoped to the given book instead.
 *
 * <p>Every eviction is deferred to after the enclosing transaction commits. Evicting inside the
 * transaction would open a window where a concurrent read misses the cache, re-reads the
 * database under the writer's snapshot (not-yet-committed rows aren't visible to it), and
 * repopulates the cache with the same stale values right before commit - with no further
 * eviction scheduled, that stale entry would then sit until the safety-net TTL expires.
 */
@Service
@RequiredArgsConstructor
public class CacheEvictionService {

  private final CacheManager cacheManager;

  public void evictBook(UUID bookId) {
    runAfterCommit(
        () -> {
          evict(CacheConfig.BOOK_BALANCE, bookId.toString());
          evictByBookPrefix(CacheConfig.MONTHLY_SUMMARY, bookId);
          evictByBookPrefix(CacheConfig.BUDGETS, bookId);
        });
  }

  public void evictBudgetMonth(UUID bookId, String yearMonth) {
    runAfterCommit(
        () -> {
          evict(CacheConfig.MONTHLY_SUMMARY, bookId + ":" + yearMonth);
          evict(CacheConfig.BUDGETS, bookId + ":" + yearMonth);
        });
  }

  public void evictCategories(UUID bookId) {
    runAfterCommit(() -> evict(CacheConfig.CATEGORIES, bookId.toString()));
  }

  public void evictBooks(UUID ownerId) {
    runAfterCommit(() -> evict(CacheConfig.BOOKS, ownerId.toString()));
  }

  private void runAfterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }

  private void evict(String cacheName, Object key) {
    var cache = cacheManager.getCache(cacheName);
    if (cache != null) cache.evict(key);
  }

  private void evictByBookPrefix(String cacheName, UUID bookId) {
    var cache = cacheManager.getCache(cacheName);
    if (!(cache instanceof CaffeineCache caffeineCache)) return;

    String prefix = bookId + ":";
    Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
    nativeCache.asMap().keySet().removeIf(key -> key instanceof String s && s.startsWith(prefix));
  }
}
