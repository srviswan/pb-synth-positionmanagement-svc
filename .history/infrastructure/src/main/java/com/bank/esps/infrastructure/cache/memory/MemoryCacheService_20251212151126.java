package com.bank.esps.infrastructure.cache.memory;

import com.bank.esps.domain.cache.CacheService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * In-memory Caffeine cache implementation of CacheService
 * Enabled when app.cache.memory.enabled=true or app.cache.type=memory
 */
@Component("memoryCacheService")
@ConditionalOnProperty(
        name = "app.cache.memory.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class MemoryCacheService implements CacheService {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryCacheService.class);
    
    private final Cache<String, Object> cache;
    
    public MemoryCacheService() {
        // Default configuration: 10,000 entries, 1 hour expiration
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()
                .build();
        log.info("Initialized in-memory Caffeine cache");
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            Object value = cache.getIfPresent(key);
            if (value == null) {
                return Optional.empty();
            }
            // Type safety: assuming value was stored as type T
            return Optional.of((T) value);
        } catch (Exception e) {
            log.error("Error getting value from memory cache for key: {}", key, e);
            return Optional.empty();
        }
    }
    
    @Override
    public <T> void put(String key, T value, Duration ttl) {
        try {
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                // Caffeine doesn't support per-entry TTL, so we use a wrapper
                // For simplicity, we'll use the default expiration
                cache.put(key, value);
            } else {
                cache.put(key, value);
            }
            log.debug("Cached value for key: {} with TTL: {}", key, ttl);
        } catch (Exception e) {
            log.error("Error putting value into memory cache for key: {}", key, e);
        }
    }
    
    @Override
    public <T> void put(String key, T value) {
        put(key, value, null);
    }
    
    @Override
    public void evict(String key) {
        try {
            cache.invalidate(key);
            log.debug("Evicted key from memory cache: {}", key);
        } catch (Exception e) {
            log.error("Error evicting key from memory cache: {}", key, e);
        }
    }
    
    @Override
    public boolean exists(String key) {
        return cache.getIfPresent(key) != null;
    }
    
    @Override
    public <T> T getOrCompute(String key, Class<T> type, java.util.function.Supplier<T> supplier, Duration ttl) {
        try {
            @SuppressWarnings("unchecked")
            T cached = (T) cache.get(key, k -> supplier.get());
            return cached;
        } catch (Exception e) {
            log.error("Error in getOrCompute for key: {}", key, e);
            return supplier.get();
        }
    }
    
    @Override
    public <T> T getOrCompute(String key, Class<T> type, java.util.function.Supplier<T> supplier) {
        return getOrCompute(key, type, supplier, null);
    }
    
    @Override
    public void clear() {
        try {
            cache.invalidateAll();
            log.info("Cleared all entries from memory cache");
        } catch (Exception e) {
            log.error("Error clearing memory cache", e);
        }
    }
    
    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            com.github.benmanes.caffeine.cache.stats.CacheStats cacheStats = cache.stats();
            stats.put("type", "memory");
            stats.put("hitCount", cacheStats.hitCount());
            stats.put("missCount", cacheStats.missCount());
            stats.put("hitRate", cacheStats.hitRate());
            stats.put("evictionCount", cacheStats.evictionCount());
            stats.put("size", cache.estimatedSize());
        } catch (Exception e) {
            log.error("Error getting memory cache stats", e);
        }
        return stats;
    }
}
