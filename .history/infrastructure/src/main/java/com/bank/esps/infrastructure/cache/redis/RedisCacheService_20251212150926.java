package com.bank.esps.infrastructure.cache.redis;

import com.bank.esps.domain.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis implementation of CacheService
 * Enabled when app.cache.redis.enabled=true (default) or app.cache.type=redis
 */
@Component("redisCacheService")
@ConditionalOnProperty(
        name = "app.cache.redis.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisCacheService implements CacheService {
    
    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public RedisCacheService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            T result = objectMapper.readValue(value, type);
            return Optional.of(result);
        } catch (Exception e) {
            log.error("Error getting value from Redis cache for key: {}", key, e);
            return Optional.empty();
        }
    }
    
    @Override
    public <T> void put(String key, T value, Duration ttl) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                redisTemplate.opsForValue().set(key, jsonValue, ttl.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                redisTemplate.opsForValue().set(key, jsonValue);
            }
            log.debug("Cached value for key: {} with TTL: {}", key, ttl);
        } catch (Exception e) {
            log.error("Error putting value into Redis cache for key: {}", key, e);
        }
    }
    
    @Override
    public <T> void put(String key, T value) {
        put(key, value, null); // No expiration
    }
    
    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("Evicted key from Redis cache: {}", key);
        } catch (Exception e) {
            log.error("Error evicting key from Redis cache: {}", key, e);
        }
    }
    
    @Override
    public boolean exists(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Error checking existence in Redis cache for key: {}", key, e);
            return false;
        }
    }
    
    @Override
    public <T> T getOrCompute(String key, Class<T> type, java.util.function.Supplier<T> supplier, Duration ttl) {
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        T value = supplier.get();
        if (value != null) {
            put(key, value, ttl);
        }
        return value;
    }
    
    @Override
    public <T> T getOrCompute(String key, Class<T> type, java.util.function.Supplier<T> supplier) {
        return getOrCompute(key, type, supplier, null);
    }
    
    @Override
    public void clear() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushDb();
            log.info("Cleared all entries from Redis cache");
        } catch (Exception e) {
            log.error("Error clearing Redis cache", e);
        }
    }
    
    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            // Redis doesn't provide built-in stats, but we can add custom metrics
            stats.put("type", "redis");
            stats.put("status", "connected");
        } catch (Exception e) {
            log.error("Error getting Redis cache stats", e);
        }
        return stats;
    }
}
