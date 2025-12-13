# Contract Service Integration

## Overview

The Position Management Service integrates with a separate Contract Service to retrieve tax lot allocation rules (FIFO, LIFO, HIFO) for each contract. The integration supports both real REST-based Contract Service and a mock implementation for testing.

## Architecture

### Abstraction Layer

- **Interface**: `com.bank.esps.domain.contract.ContractService`
- **Real Implementation**: `com.bank.esps.infrastructure.contract.ContractServiceClient` (REST client)
- **Mock Implementation**: `com.bank.esps.infrastructure.contract.MockContractService` (in-memory)

### Service Selection

The implementation is selected via configuration:

```yaml
app:
  contract:
    service:
      type: ${CONTRACT_SERVICE_TYPE:mock}  # "rest" or "mock"
      url: ${CONTRACT_SERVICE_URL:http://localhost:8082/api/contracts}
      enabled: ${CONTRACT_SERVICE_ENABLED:true}
    cache:
      enabled: ${CONTRACT_CACHE_ENABLED:true}
```

## REST Implementation (`ContractServiceClient`)

### Features

- **REST API Integration**: Calls external Contract Service via HTTP
- **Circuit Breaker**: Integrated with Resilience4j circuit breaker
- **Timeout Handling**: 5s connect timeout, 10s read timeout
- **Graceful Fallback**: Returns default FIFO rules on errors
- **Health Check**: Monitors Contract Service availability

### API Endpoints

The REST client expects the following Contract Service endpoints:

#### Get Contract Rules
```
GET /api/contracts/{contractId}/rules
Response: ContractRules
```

#### Update Contract Rules
```
PUT /api/contracts/{contractId}/rules
Body: ContractRules
```

#### Health Check
```
GET /health
Response: 200 OK
```

### Error Handling

- **Timeout**: Falls back to default FIFO rules
- **Connection Error**: Falls back to default FIFO rules
- **Circuit Breaker Open**: Falls back to default FIFO rules
- **404 Not Found**: Falls back to default FIFO rules
- **5xx Server Error**: Falls back to default FIFO rules

### Configuration

```yaml
app:
  contract:
    service:
      type: rest
      url: http://contract-service:8082/api/contracts
      enabled: true

resilience4j:
  circuitbreaker:
    instances:
      contractService:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
```

## Mock Implementation (`MockContractService`)

### Features

- **In-Memory Storage**: Stores contract rules in a HashMap
- **Pre-configured Contracts**: Includes FIFO, LIFO, HIFO test contracts
- **Test Utilities**: Methods for adding/removing contracts in tests
- **Always Available**: No network calls, always returns success

### Pre-configured Contracts

- `CONTRACT-FIFO`: FIFO tax lot method
- `CONTRACT-LIFO`: LIFO tax lot method
- `CONTRACT-HIFO`: HIFO tax lot method

### Test Utilities

```java
@Autowired
private MockContractService mockContractService;

// Add custom contract rule
ContractRules customRules = ContractRules.builder()
    .contractId("CUSTOM-123")
    .taxLotMethod(TaxLotMethod.LIFO)
    .build();
mockContractService.addContractRule("CUSTOM-123", customRules);

// Get contract rules
ContractRules rules = mockContractService.getContractRulesSync("CUSTOM-123");

// Clear cache (re-initializes defaults)
mockContractService.clearCache();

// Get all cached rules
Map<String, ContractRules> allRules = mockContractService.getAllCachedRules();
```

### Configuration

```yaml
app:
  contract:
    service:
      type: mock  # Default for local development
```

## Caching

Contract rules are cached to reduce calls to Contract Service:

```yaml
app:
  contract:
    cache:
      enabled: true  # Enable caching
  cache:
    default-ttl: PT24H  # 24 hours cache TTL
```

### Cache Strategy

1. **Cache-Aside Pattern**:
   - Check cache first
   - If miss, call Contract Service
   - Store result in cache
   - Return rules

2. **Cache Update**:
   - When contract rules are updated, both cache and Contract Service are updated
   - Cache is updated synchronously
   - Contract Service update is asynchronous

## Usage in Position Service

### Getting Contract Rules

```java
@Service
public class HotpathPositionService {
    
    private final ContractRulesService contractRulesService;
    
    public void processTrade(TradeEvent tradeEvent) {
        // Get contract rules (cached if available)
        ContractRules rules = contractRulesService.getContractRules(
            tradeEvent.getContractId()
        );
        
        // Use rules for tax lot allocation
        LotAllocationResult result = lotLogic.reduceLots(
            state, 
            tradeEvent.getQuantity(), 
            rules, 
            tradeEvent.getPrice()
        );
    }
}
```

### Updating Contract Rules

```java
// When contract events are received
contractRulesService.updateContractRules(contractRules);
```

## Testing

### Unit Tests with Mock

```java
@SpringBootTest
class PositionServiceTest {
    
    @Autowired
    private MockContractService mockContractService;
    
    @Test
    void testWithCustomContract() {
        // Setup
        ContractRules customRules = ContractRules.builder()
            .contractId("TEST-CONTRACT")
            .taxLotMethod(TaxLotMethod.LIFO)
            .build();
        mockContractService.addContractRule("TEST-CONTRACT", customRules);
        
        // Test
        // ... use contract in trade processing
    }
}
```

### Integration Tests with REST

```yaml
# application-test.yml
app:
  contract:
    service:
      type: rest
      url: http://localhost:8082/api/contracts
      enabled: true
```

## Monitoring

### Health Check

```java
boolean isAvailable = contractRulesService.isContractServiceAvailable();
```

### Circuit Breaker Metrics

Circuit breaker state is exposed via:
- `/actuator/health` - Health indicator
- `/actuator/metrics/resilience4j.circuitbreaker.calls` - Call metrics
- `/actuator/metrics` - Detailed metrics

## Best Practices

1. **Always Use Abstraction**: Use `ContractService` interface, never direct implementation
2. **Cache Enabled in Production**: Enable caching to reduce Contract Service load
3. **Monitor Circuit Breaker**: Set up alerts for circuit breaker state changes
4. **Use Mock for Tests**: Use `MockContractService` for unit tests
5. **Graceful Degradation**: System continues to work with default FIFO rules if Contract Service is unavailable

## Contract Service API Contract

### ContractRules Model

```java
public class ContractRules {
    private String contractId;
    private TaxLotMethod taxLotMethod;  // FIFO, LIFO, HIFO
    // ... other fields
}
```

### Expected Response Format

```json
{
  "contractId": "CONTRACT-123",
  "taxLotMethod": "FIFO"
}
```

## Troubleshooting

### Contract Service Unavailable

- Check `app.contract.service.enabled: true`
- Verify Contract Service URL is correct
- Check network connectivity
- Review circuit breaker state
- System will fall back to default FIFO rules

### Cache Issues

- Clear cache: `contractRulesService.clearCache()`
- Disable cache: `app.contract.cache.enabled: false`
- Check cache TTL: `app.cache.default-ttl`

### Mock Service Not Working

- Verify `app.contract.service.type: mock`
- Check that `MockContractService` is being used (check logs)
- Use `addContractRule()` to add test contracts
