# Contract Service Integration

## Overview

The Position Management Service integrates with an external Contract Service to retrieve contract rules (tax lot allocation methods: FIFO, LIFO, HIFO). The integration is abstracted to support both real Contract Service (REST) and mock implementation for testing.

## Architecture

### Abstraction Layer

- **Interface**: `com.bank.esps.domain.contract.ContractService`
- **Real Implementation**: `com.bank.esps.infrastructure.contract.ContractServiceClient` (REST client)
- **Mock Implementation**: `com.bank.esps.infrastructure.contract.MockContractService` (in-memory)

### Service Layer

- **ContractRulesService**: Orchestrates Contract Service calls and caching
  - Checks cache first (if enabled)
  - Falls back to Contract Service if cache miss
  - Caches results for performance

## Configuration

### application.yml

```yaml
app:
  contract:
    service:
      type: mock  # "rest" for real service, "mock" for testing
      url: http://localhost:8082/api/contracts  # Only used for "rest" type
      enabled: true
    cache:
      enabled: true  # Enable caching of contract rules
```

### Environment Variables

- `CONTRACT_SERVICE_TYPE`: "rest" or "mock" (default: "mock")
- `CONTRACT_SERVICE_URL`: Contract Service base URL (default: "http://localhost:8082/api/contracts")
- `CONTRACT_SERVICE_ENABLED`: Enable/disable contract service (default: true)
- `CONTRACT_CACHE_ENABLED`: Enable/disable caching (default: true)

## Usage

### Real Contract Service (Production)

```yaml
app:
  contract:
    service:
      type: rest
      url: http://contract-service:8082/api/contracts
      enabled: true
```

The `ContractServiceClient` will:
1. Make REST calls to Contract Service
2. Handle errors gracefully (falls back to default FIFO rules)
3. Cache results for performance

### Mock Contract Service (Testing/Development)

```yaml
app:
  contract:
    service:
      type: mock
      enabled: true
```

The `MockContractService` will:
1. Store contract rules in-memory
2. Return default FIFO rules if contract not found
3. Pre-populated with test contracts:
   - `CONTRACT-FIFO` → FIFO method
   - `CONTRACT-LIFO` → LIFO method
   - `CONTRACT-HIFO` → HIFO method

## API Contract

### Contract Service REST API (Expected)

```
GET /api/contracts/{contractId}/rules
Response: ContractRules {
  "contractId": "string",
  "taxLotMethod": "FIFO" | "LIFO" | "HIFO",
  "businessRules": { ... }
}

PUT /api/contracts/{contractId}/rules
Request Body: ContractRules
```

### Health Check

```
GET /health
Response: 200 OK
```

## Caching

Contract rules are cached to reduce calls to Contract Service:

- **Cache Key**: `contract:rules:{contractId}`
- **TTL**: Configurable via `app.cache.default-ttl` (default: 24 hours)
- **Cache Implementation**: Uses `CacheService` abstraction (Redis or Caffeine)

## Error Handling

- **Service Unavailable**: Falls back to default FIFO rules
- **Invalid Contract ID**: Returns default FIFO rules
- **Network Errors**: Logs error and returns default FIFO rules
- **Cache Errors**: Bypasses cache and calls Contract Service directly

## Testing

### Unit Tests

Use `MockContractService` for unit tests:

```java
@Autowired
private MockContractService mockContractService;

@Test
public void testWithCustomContract() {
    // Add custom contract rule
    ContractRules customRules = ContractRules.builder()
        .contractId("CUSTOM-123")
        .taxLotMethod(TaxLotMethod.LIFO)
        .build();
    
    mockContractService.addContractRule("CUSTOM-123", customRules);
    
    // Test position service with custom contract
    // ...
}
```

### Integration Tests

For integration tests, you can:
1. Use `MockContractService` (default)
2. Use real Contract Service by setting `CONTRACT_SERVICE_TYPE=rest`
3. Use Testcontainers to spin up Contract Service

## Benefits

1. **Testability**: Easy to test with mock implementation
2. **Flexibility**: Switch between real and mock via configuration
3. **Performance**: Caching reduces Contract Service calls
4. **Resilience**: Graceful fallback to default rules on errors
5. **Separation of Concerns**: Contract Service integration is isolated

## Future Enhancements

1. **Circuit Breaker**: Add Resilience4j circuit breaker for Contract Service calls
2. **Retry Logic**: Automatic retry on transient failures
3. **Metrics**: Track Contract Service call latency and error rates
4. **Event-Driven Updates**: Listen to contract events to invalidate cache
5. **Bulk Loading**: Pre-load contract rules on startup
