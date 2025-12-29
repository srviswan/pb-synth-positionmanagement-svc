package com.bank.esps.infrastructure.config;

import com.bank.esps.domain.cache.CacheService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration to select cache implementation (Redis or in-memory)
 */
@Configuration
public class CacheConfig {
    
    @Value("${app.cache.provider:redis}")
    private String cacheProvider;
    
    @Bean
    @Primary
    public CacheService cacheService(
            @Qualifier("redisCacheService") CacheService redisCache,
            @Qualifier("memoryCacheService") CacheService memoryCache) {
        if ("memory".equalsIgnoreCase(cacheProvider)) {
            return memoryCache;
        }
        return redisCache; // Default to Redis
    }
}
