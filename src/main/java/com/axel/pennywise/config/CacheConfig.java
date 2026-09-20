package com.axel.pennywise.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Invalidation-driven read caching (see DESIGN.md). Every cache carries a generous expireAfterWrite
 * as a safety net only - correctness relies on explicit eviction in CacheEvictionService and the
 * write paths that call it, not on this TTL.
 */
@Configuration
@EnableCaching
public class CacheConfig {

  public static final String BOOK_BALANCE = "bookBalance";
  public static final String MONTHLY_SUMMARY = "monthlySummary";
  public static final String BUDGETS = "budgets";
  public static final String CATEGORIES = "categories";
  public static final String BOOKS = "books";

  private static final Duration SAFETY_NET_TTL = Duration.ofMinutes(60);

  @Bean
  public CacheManagerCustomizer<CaffeineCacheManager> caffeineCacheManagerCustomizer() {
    return cacheManager -> {
      cacheManager.setCacheNames(
          List.of(BOOK_BALANCE, MONTHLY_SUMMARY, BUDGETS, CATEGORIES, BOOKS));
      cacheManager.setCaffeine(
          Caffeine.newBuilder().maximumSize(500).expireAfterWrite(SAFETY_NET_TTL));
    };
  }
}
