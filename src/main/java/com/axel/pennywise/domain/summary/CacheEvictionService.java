package com.axel.pennywise.domain.summary;

import com.axel.pennywise.config.CacheConfig;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

/**
 * Evicts every cached read that can go stale after a transaction/category-name write for a
 * book. Transaction writes and category renames can each touch an unbounded set of months (an
 * edit can move a transaction across a month boundary, an import can span many months), so
 * per-month keys aren't safe to target - this walks each cache and drops every entry whose key
 * is scoped to the given book instead.
 */
@Service
@RequiredArgsConstructor
public class CacheEvictionService {

  private final CacheManager cacheManager;

  public void evictBook(UUID bookId) {
    evict(CacheConfig.BOOK_BALANCE, bookId.toString());
    evictByBookPrefix(CacheConfig.MONTHLY_SUMMARY, bookId);
    evictByBookPrefix(CacheConfig.BUDGETS, bookId);
  }

  public void evictBudgetMonth(UUID bookId, String yearMonth) {
    evict(CacheConfig.MONTHLY_SUMMARY, bookId + ":" + yearMonth);
    evict(CacheConfig.BUDGETS, bookId + ":" + yearMonth);
  }

  public void evictCategories(UUID bookId) {
    evict(CacheConfig.CATEGORIES, bookId.toString());
  }

  public void evictBooks(UUID ownerId) {
    evict(CacheConfig.BOOKS, ownerId.toString());
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
