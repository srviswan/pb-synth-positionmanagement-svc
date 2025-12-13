package com.bank.esps.domain.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction for caching operations
 * Allows switching between cache implementations (Redis, Caffeine, Hazelcast, etc.)
 * without changing application code
 */
public interface CacheService {
    
    /**
     * Get value from cache
     * @param key Cache key
     * @param type Value type
     * @return Optional value if present
     */
    <T> Optional<T> get(String key, Class<T> type);
    
    /**
     * Put value into cache
     * @param key Cache key
     * @param value Value to cache
     * @param ttl Time to live (optional, null means no expiration)
     */
    <T> void put(String key, T value, Duration ttl);
    
    /**
     * Put value into cache with default TTL
     * @param key Cache key
     * @param value Value to cache
     */
    <T> void put(String key, T value);
    
    /**
     * Remove value from cache
     * @param key Cache key
     */
    void evict(String key);
    
    /**
     * Check if key exists in cache
     * @param key Cache key
     * @return true if key exists
     */
    boolean exists(String key);
    
    /**
     * Get value from cache, or compute and cache if absent
     * @param key Cache key
     * @param type Value type
     * @param supplier Function to compute value if not in cache
     * @param ttl Time to live for computed value
     * @return Cached or computed value
     */
    <T> T getOrCompute(String key, Class<T> type, java.util.function.Supplier<T> supplier, Duration ttl);
    
    /**
     * Get value from cache, or compute and cache if absent (with default TTL)
     * @param key Cache key
     * @param type Value type
     * @param supplier Function to compute value if not in cache
     * @return Cached or computed value
     */
    <T> T getOrCompute(String key, Class<T> type, java.util.function.Supplier<T> supplier);
    
    /**
     * Clear all entries from cache
     */
    void clear();
    
    /**
     * Get cache statistics (if supported)
     * @return Cache statistics or empty map
     */
    java.util.Map<String, Object> getStats();
}
