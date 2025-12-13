package com.bank.esps.application.config;

import com.bank.esps.domain.cache.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Cache configuration
 * Wires up the cache abstraction with implementations based on application.yml configuration
 * 
 * Configuration in application.yml:
 *   app:
 *     cache:
 *       type: redis  # or memory
 *       redis:
 *         enabled: true
 *       memory:
 *         enabled: false
 * 
 * To switch cache systems, simply change the type in application.yml:
 *   app.cache.type: memory
 * 
 * Or use environment variables:
 *   CACHE_TYPE=memory
 */
@Configuration
public class CacheConfig {
    
    @Value("${app.cache.type:redis}")
    private String cacheType;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * Primary CacheService bean
     * Selected based on app.cache.type property
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "cacheService")
    public CacheService cacheService(
            @Qualifier("redisCacheService") CacheService redisCache) {
        
        String type = cacheType.toLowerCase();
        if ("memory".equals(type)) {
            // Try to get memory cache if available
            try {
                CacheService memoryCache = applicationContext.getBean("memoryCacheService", CacheService.class);
                return memoryCache;
            } catch (Exception e) {
                throw new IllegalStateException("Memory cache is not available. " +
                        "Ensure app.cache.memory.enabled=true and Caffeine dependencies are present.", e);
            }
        } else {
            // Default to Redis
            return redisCache;
        }
    }
}
