package com.bank.esps.domain.cache;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Abstraction for caching services.
 * Allows switching between Redis, in-memory, or other caching implementations.
 */
public interface CacheService {
    
    /**
     * Get a value from cache
     * @param key The cache key
     * @param type The expected type
     * @return Optional containing the value if found
     */
    <T> Optional<T> get(String key, Class<T> type);
    
    /**
     * Put a value in cache
     * @param key The cache key
     * @param value The value to cache
     */
    void put(String key, Object value);
    
    /**
     * Put a value in cache with expiration
     * @param key The cache key
     * @param value The value to cache
     * @param timeout The expiration time
     * @param unit The time unit
     */
    void put(String key, Object value, long timeout, TimeUnit unit);
    
    /**
     * Remove a value from cache
     * @param key The cache key
     */
    void evict(String key);
    
    /**
     * Check if a key exists in cache
     * @param key The cache key
     * @return true if key exists
     */
    boolean exists(String key);
    
    /**
     * Clear all cache entries
     */
    void clear();
}
