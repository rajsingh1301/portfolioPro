package com.portfoliopro.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfoliopro.market.FinnhubClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Two caches with different lifetimes: a quote is only useful while it is fresh, but
 * the set of symbols matching a search barely changes from day to day. Slice 6's
 * pending-order scheduler reads the quote cache too, so it adds no upstream load.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${app.cache.quote-ttl-seconds:15}") long quoteTtlSeconds,
            @Value("${app.cache.search-ttl-minutes:60}") long searchTtlMinutes) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                caffeineCache(FinnhubClient.QUOTE_CACHE, Duration.ofSeconds(quoteTtlSeconds), 500),
                caffeineCache(FinnhubClient.SEARCH_CACHE, Duration.ofMinutes(searchTtlMinutes), 200)));
        return manager;
    }

    private static CaffeineCache caffeineCache(String name, Duration ttl, int maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build());
    }
}
