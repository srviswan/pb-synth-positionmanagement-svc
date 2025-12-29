package com.bank.esps.infrastructure.cache.redis;

import com.bank.esps.domain.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis implementation of CacheService
 */
@Component("redisCacheService")
public class RedisCacheService implements CacheService {
    
    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null && type.isInstance(value)) {
                return Optional.of(type.cast(value));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error getting value from Redis for key: {}", key, e);
            return Optional.empty();
        }
    }
    
    @Override
    public void put(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("Error putting value to Redis for key: {}", key, e);
            throw new RuntimeException("Failed to cache value", e);
        }
    }
    
    @Override
    public void put(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.error("Error putting value to Redis with expiration for key: {}", key, e);
            throw new RuntimeException("Failed to cache value with expiration", e);
        }
    }
    
    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Error evicting key from Redis: {}", key, e);
        }
    }
    
    @Override
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Error checking existence of key in Redis: {}", key, e);
            return false;
        }
    }
    
    @Override
    public void clear() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
        } catch (Exception e) {
            log.error("Error clearing Redis cache", e);
        }
    }
}
