# Cache Abstraction Layer

## Overview

The cache abstraction layer allows the application to be independent of the underlying cache implementation (Redis, Caffeine, Hazelcast, etc.). This enables switching cache systems without changing application code.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Application Layer                       │
│  (ContractRulesService, IdempotencyService)             │
│                    ↓ uses                                │
│  ┌──────────────────────────────────────────────────┐ │
│  │              CacheService                         │ │
│  │     (Interface in domain module)                  │ │
│  └──────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
                    ↓ implements
┌─────────────────────────────────────────────────────────┐
│              Infrastructure Layer                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  RedisCacheService (Redis implementation)         │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  MemoryCacheService (Caffeine implementation)     │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## Interface

### CacheService

Located in: `com.bank.esps.domain.cache.CacheService`

**Key Methods:**
- `get(String key, Class<T> type)` - Get value from cache
- `put(String key, T value, Duration ttl)` - Put value with TTL
- `put(String key, T value)` - Put value (no expiration)
- `evict(String key)` - Remove from cache
- `exists(String key)` - Check if key exists
- `getOrCompute(String key, Class<T> type, Supplier<T> supplier, Duration ttl)` - Get or compute
- `clear()` - Clear all entries
- `getStats()` - Get cache statistics

## Current Implementations

### Redis Cache (Default)
- **Bean Name**: `redisCacheService`
- **Enabled When**: `app.cache.redis.enabled=true` (default)
- **Features**: Distributed cache, persistence, TTL support
- **Use Case**: Production, multi-instance deployments

### Memory Cache (Caffeine)
- **Bean Name**: `memoryCacheService`
- **Enabled When**: `app.cache.memory.enabled=true`
- **Features**: High-performance in-memory cache, statistics
- **Use Case**: Development, single-instance, high-performance scenarios

## Configuration in application.yml

```yaml
app:
  cache:
    # Type: redis, memory, hazelcast, etc.
    # Default: redis
    type: ${CACHE_TYPE:redis}
    
    # Enable/disable specific implementations
    redis:
      enabled: ${CACHE_REDIS_ENABLED:true}
    memory:
      enabled: ${CACHE_MEMORY_ENABLED:false}
    
    # Cache configuration
    default-ttl: ${CACHE_DEFAULT_TTL:1h}  # Default TTL for cached entries
    max-size: ${CACHE_MAX_SIZE:10000}     # Max entries for in-memory cache
```

## Usage Examples

### Use Redis (Default)

```yaml
app:
  cache:
    type: redis
    redis:
      enabled: true
    memory:
      enabled: false
```

Or simply omit the configuration (Redis is default).

### Switch to Memory Cache

```yaml
app:
  cache:
    type: memory
    redis:
      enabled: false
    memory:
      enabled: true
```

### Use Environment Variables

```bash
# Set cache type
export CACHE_TYPE=memory
export CACHE_REDIS_ENABLED=false
export CACHE_MEMORY_ENABLED=true

# Run application
mvn spring-boot:run
```

## Using CacheService in Your Code

### Example: ContractRulesService

```java
@Service
public class ContractRulesService {
    private final CacheService cacheService;
    
    public ContractRules getContractRules(String contractId) {
        String cacheKey = "contract:rules:" + contractId;
        
        return cacheService.getOrCompute(
            cacheKey,
            ContractRules.class,
            () -> ContractRules.defaultRules(contractId),
            Duration.ofHours(24)
        );
    }
}
```

### Example: Manual Cache Operations

```java
@Service
public class MyService {
    private final CacheService cacheService;
    
    public void example() {
        // Put value with TTL
        cacheService.put("key", value, Duration.ofMinutes(30));
        
        // Get value
        Optional<MyClass> cached = cacheService.get("key", MyClass.class);
        
        // Check existence
        if (cacheService.exists("key")) {
            // ...
        }
        
        // Evict
        cacheService.evict("key");
    }
}
```

## Adding New Cache Implementations

### Step 1: Create Implementation

```java
@Component("hazelcastCacheService")
@ConditionalOnProperty(
    name = "app.cache.hazelcast.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class HazelcastCacheService implements CacheService {
    // Implement all CacheService methods
}
```

### Step 2: Add Configuration

```yaml
app:
  cache:
    type: hazelcast
    hazelcast:
      enabled: true
```

### Step 3: Update CacheConfig

Add case for new type in `CacheConfig.java`:

```java
if ("hazelcast".equals(type)) {
    return applicationContext.getBean("hazelcastCacheService", CacheService.class);
}
```

## Benefits

✅ **Decoupling**: Application code doesn't depend on cache implementation  
✅ **Testability**: Easy to mock for unit tests  
✅ **Flexibility**: Switch cache systems without code changes  
✅ **Performance**: Choose optimal cache for your use case  
✅ **Clean Architecture**: Follows dependency inversion principle  

## Performance Considerations

### Redis
- **Pros**: Distributed, persistent, shared across instances
- **Cons**: Network latency, requires Redis server
- **Best For**: Production, multi-instance deployments

### Memory (Caffeine)
- **Pros**: Very fast, no network overhead, built-in statistics
- **Cons**: Not shared, lost on restart, limited by heap
- **Best For**: Development, single-instance, high-performance scenarios

## Cache Statistics

Both implementations support statistics:

```java
Map<String, Object> stats = cacheService.getStats();
// Redis: type, status
// Memory: type, hitCount, missCount, hitRate, evictionCount, size
```

## Migration Path

1. **Phase 1**: Use Redis in production, Memory in development
2. **Phase 2**: Profile and optimize based on statistics
3. **Phase 3**: Switch implementations as needed without code changes

## Troubleshooting

### Error: "Memory cache is not available"

**Cause**: `app.cache.type=memory` but Memory is not enabled or dependencies missing

**Solution**:
1. Set `app.cache.memory.enabled=true`
2. Ensure Caffeine dependency is in `pom.xml`
3. Or switch back to Redis: `app.cache.type=redis`

### Cache Not Working

**Check**:
1. Cache implementation is enabled in `application.yml`
2. Cache type matches enabled implementation
3. Redis connection (if using Redis) is configured correctly
4. Check logs for cache errors
