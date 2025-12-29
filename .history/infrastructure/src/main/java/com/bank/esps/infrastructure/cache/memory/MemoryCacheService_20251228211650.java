package com.bank.esps.infrastructure.cache.memory;

import com.bank.esps.domain.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * In-memory implementation of CacheService using ConcurrentHashMap
 */
@Component("memoryCacheService")
public class MemoryCacheService implements CacheService {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryCacheService.class);
    
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        
        // Check if expired
        if (entry.isExpired()) {
            cache.remove(key);
            return Optional.empty();
        }
        
        Object value = entry.getValue();
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }
    
    @Override
    public void put(String key, Object value) {
        cache.put(key, new CacheEntry(value, -1));
    }
    
    @Override
    public void put(String key, Object value, long timeout, TimeUnit unit) {
        long expirationTime = System.currentTimeMillis() + unit.toMillis(timeout);
        cache.put(key, new CacheEntry(value, expirationTime));
        
        // Schedule cleanup
        scheduler.schedule(() -> {
            CacheEntry entry = cache.get(key);
            if (entry != null && entry.isExpired()) {
                cache.remove(key);
            }
        }, timeout, unit);
    }
    
    @Override
    public void evict(String key) {
        cache.remove(key);
    }
    
    @Override
    public boolean exists(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            return false;
        }
        return true;
    }
    
    @Override
    public void clear() {
        cache.clear();
    }
    
    private static class CacheEntry {
        private final Object value;
        private final long expirationTime;
        
        CacheEntry(Object value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }
        
        Object getValue() {
            return value;
        }
        
        boolean isExpired() {
            return expirationTime > 0 && System.currentTimeMillis() > expirationTime;
        }
    }
}
