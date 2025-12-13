# Refactoring Opportunities

## Executive Summary

This document identifies refactoring opportunities to improve code maintainability, testability, and performance. The codebase is functional but has several areas that could benefit from refactoring.

## Priority Levels

- **🔴 High Priority**: Impacts maintainability, testability, or performance significantly
- **🟡 Medium Priority**: Improves code quality but not critical
- **🟢 Low Priority**: Nice-to-have improvements

---

## 1. Service Class Size and Complexity 🔴

### Issue
- `HotpathPositionService`: 703 lines
- `RecalculationService`: 931 lines
- `processCurrentDatedTrade()`: ~200 lines
- `recalculatePosition()`: ~200 lines

### Impact
- Hard to test individual responsibilities
- Difficult to understand and maintain
- High cognitive load

### Refactoring Strategy

#### 1.1 Extract Snapshot Management
```java
@Service
public class SnapshotService {
    public SnapshotEntity loadOrCreateSnapshot(String positionKey, TradeEvent tradeEvent);
    public PositionState inflateSnapshot(SnapshotEntity snapshot);
    public SnapshotEntity compressAndSave(PositionState state, SnapshotEntity snapshot);
    public void updatePriceQuantitySchedule(SnapshotEntity snapshot, PositionState state);
}
```

#### 1.2 Extract Event Management
```java
@Service
public class EventStoreService {
    public EventEntity createEventEntity(TradeEvent tradeEvent, long version, LotAllocationResult result);
    public void saveEvent(EventEntity event);
    public List<EventEntity> loadEventStream(String positionKey);
}
```

#### 1.3 Extract Position Lifecycle Management
```java
@Service
public class PositionLifecycleService {
    public void handlePositionClosure(SnapshotEntity snapshot, TradeEvent tradeEvent);
    public void handlePositionReopening(SnapshotEntity snapshot, TradeEvent tradeEvent);
    public boolean shouldClosePosition(PositionState state);
    public boolean shouldReopenPosition(SnapshotEntity snapshot, TradeEvent tradeEvent);
}
```

#### 1.4 Extract UPI Management
```java
@Service
public class UPIManagementService {
    public void handleUPICreation(String positionKey, String upi, TradeEvent tradeEvent);
    public void handleUPITermination(String positionKey, String upi, TradeEvent tradeEvent);
    public void handleUPIReopening(String positionKey, String newUPI, String previousUPI, TradeEvent tradeEvent);
    public void handleUPIInvalidation(String positionKey, String previousUPI, String newUPI, TradeEvent backdatedTrade);
}
```

**Benefits**:
- Single Responsibility Principle
- Easier unit testing
- Better code reuse
- Reduced complexity

---

## 2. Code Duplication 🔴

### Issue
- Similar patterns for event creation in both services
- Duplicate snapshot inflation/compression logic
- Repeated error handling patterns
- Similar UPI history recording patterns

### Examples

#### 2.1 Event Entity Creation
Both `HotpathPositionService` and `RecalculationService` have similar `createEventEntity()` methods.

**Solution**: Extract to `EventStoreService`

#### 2.2 Snapshot Inflation
Both services have similar snapshot inflation logic.

**Solution**: Extract to `SnapshotService`

#### 2.3 Error Handling
Repeated try-catch patterns with similar logging.

**Solution**: Use AOP for error handling or extract to utility class

```java
@Aspect
@Component
public class ServiceErrorHandler {
    @Around("@annotation(Transactional)")
    public Object handleServiceErrors(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Exception e) {
            log.error("Error in {}: {}", joinPoint.getSignature(), e.getMessage(), e);
            // Handle common error scenarios
            throw e;
        }
    }
}
```

---

## 3. Manual JSON String Building 🟡

### Issue
`RegulatorySubmissionService.createTradeReportEvent()` uses `String.format()` to build JSON.

### Current Code
```java
String regulatoryEvent = String.format(
    "{\"type\":\"TRADE_REPORT\"," +
    "\"submissionId\":\"%s\"," +
    // ... many more fields
);
```

### Problems
- Error-prone (missing quotes, escaping issues)
- Hard to maintain
- No validation
- Difficult to add/remove fields

### Solution
```java
private String createTradeReportEvent(TradeEvent tradeEvent, SnapshotEntity snapshot, UUID submissionId) {
    TradeReportEvent event = TradeReportEvent.builder()
        .type("TRADE_REPORT")
        .submissionId(submissionId)
        .tradeId(tradeEvent.getTradeId())
        // ... other fields
        .build();
    
    return objectMapper.writeValueAsString(event);
}
```

**Create Domain Model**:
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeReportEvent {
    private String type;
    private UUID submissionId;
    private String tradeId;
    // ... other fields
}
```

**Benefits**:
- Type safety
- Automatic escaping
- Easy to extend
- Validation support

---

## 4. Field Injection in Controllers 🟡

### Issue
`EventStoreController` uses `@Autowired` field injection instead of constructor injection.

### Current Code
```java
@RestController
public class EventStoreController {
    @Autowired
    private EventStoreRepository eventStoreRepository;
    
    @Autowired
    private SnapshotRepository snapshotRepository;
    // ...
}
```

### Solution
```java
@RestController
public class EventStoreController {
    private final EventStoreRepository eventStoreRepository;
    private final SnapshotRepository snapshotRepository;
    
    public EventStoreController(
            EventStoreRepository eventStoreRepository,
            SnapshotRepository snapshotRepository) {
        this.eventStoreRepository = eventStoreRepository;
        this.snapshotRepository = snapshotRepository;
    }
}
```

**Benefits**:
- Immutability
- Easier testing
- Spring best practice
- Compile-time dependency checking

---

## 5. Large Constructor Parameter Lists 🟡

### Issue
Services have 7-8 constructor parameters.

### Example
```java
public HotpathPositionService(
        EventStoreRepository eventStoreRepository,
        SnapshotRepository snapshotRepository,
        LotLogic lotLogic,
        ContractRulesService contractRulesService,
        IdempotencyService idempotencyService,
        RegulatorySubmissionService regulatorySubmissionService,
        UPIHistoryService upiHistoryService,
        ObjectMapper objectMapper) {
    // ...
}
```

### Solution Options

#### Option 1: Configuration Object
```java
@ConfigurationProperties(prefix = "position.service")
public class PositionServiceConfig {
    // Configuration properties
}

public HotpathPositionService(
        PositionServiceConfig config,
        EventStoreRepository eventStoreRepository,
        SnapshotRepository snapshotRepository,
        // ... core dependencies only
) {
    // ...
}
```

#### Option 2: Service Facade
```java
@Service
public class PositionServiceFacade {
    private final EventStoreRepository eventStoreRepository;
    private final SnapshotRepository snapshotRepository;
    // ... all dependencies
    
    public HotpathPositionService createHotpathService() {
        return new HotpathPositionService(/* ... */);
    }
}
```

**Benefits**:
- Reduced parameter count
- Better organization
- Easier to test

---

## 6. Scattered ObjectMapper Configuration 🟡

### Issue
ObjectMapper configuration is scattered across services.

### Current Code
```java
// In HotpathPositionService constructor
if (objectMapper.getDeserializationConfig().isEnabled(...)) {
    this.objectMapper = objectMapper.copy();
    this.objectMapper.configure(...);
}

// In HotpathPositionService.inflateSnapshot()
ObjectMapper deserializingMapper = new ObjectMapper();
deserializingMapper.disable(...);
```

### Solution
Create a centralized `ObjectMapper` bean with proper configuration:

```java
@Configuration
public class JacksonConfig {
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
}
```

**Benefits**:
- Single source of truth
- Consistent configuration
- Easier to maintain

---

## 7. Complex Method Extraction 🔴

### Issue
Large methods with multiple responsibilities.

### Examples

#### 7.1 `processCurrentDatedTrade()` (200+ lines)
**Extract**:
- `validateTradeAndPosition()`
- `applyTradeToPosition()`
- `updatePositionStatus()`
- `persistEventAndSnapshot()`
- `handlePostProcessing()`

#### 7.2 `recalculatePosition()` (200+ lines)
**Extract**:
- `loadAndPrepareEventStream()`
- `replayEventsWithUPITracking()`
- `createCorrectedSnapshot()`
- `handleUPIChanges()`
- `publishRegulatoryEvents()`

**Benefits**:
- Better readability
- Easier testing
- Single Responsibility Principle

---

## 8. Error Handling Patterns 🟡

### Issue
Repeated try-catch blocks with similar patterns.

### Current Pattern
```java
try {
    upiHistoryService.recordUPICreation(...);
} catch (Exception e) {
    log.error("Error recording UPI creation", e);
    // Don't throw - doesn't block processing
}
```

### Solution Options

#### Option 1: AOP for Non-Critical Operations
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NonBlocking {
}

@Aspect
@Component
public class NonBlockingAspect {
    @Around("@annotation(NonBlocking)")
    public Object handleNonBlocking(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Exception e) {
            log.error("Non-blocking operation failed: {}", joinPoint.getSignature(), e);
            return null; // Don't throw
        }
    }
}
```

#### Option 2: Utility Method
```java
public class ServiceUtils {
    public static void executeNonBlocking(Runnable operation, String operationName) {
        try {
            operation.run();
        } catch (Exception e) {
            log.error("Non-blocking operation {} failed", operationName, e);
        }
    }
}

// Usage
ServiceUtils.executeNonBlocking(
    () -> upiHistoryService.recordUPICreation(...),
    "UPI creation recording"
);
```

**Benefits**:
- DRY principle
- Consistent error handling
- Less boilerplate

---

## 9. Transaction Management Consistency 🟡

### Issue
Inconsistent use of `@Transactional` propagation and rollback policies.

### Current State
- Some methods use `@Transactional(rollbackFor = Exception.class)`
- Some use default `@Transactional`
- UPI history uses `REQUIRES_NEW` (good!)
- Some methods don't have explicit transaction boundaries

### Solution
Create a transaction management strategy:

```java
public class TransactionConfig {
    // Main transaction (hotpath/coldpath)
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    
    // History/audit transactions
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    
    // Read-only queries
    @Transactional(readOnly = true)
}
```

**Benefits**:
- Consistent behavior
- Clear transaction boundaries
- Better error handling

---

## 10. Logging Improvements 🟢

### Issue
- Inconsistent log levels
- Missing structured logging
- No correlation IDs in logs

### Solution

#### 10.1 Structured Logging
```java
// Instead of
log.info("Processing trade {} for position {}", tradeId, positionKey);

// Use
log.info("Processing trade", 
    kv("tradeId", tradeId),
    kv("positionKey", positionKey),
    kv("tradeType", tradeType)
);
```

#### 10.2 MDC for Correlation IDs
```java
@Component
public class CorrelationIdFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

**Benefits**:
- Better observability
- Easier debugging
- Trace correlation

---

## 11. Validation Logic Extraction 🟡

### Issue
Validation logic scattered in service methods.

### Current Code
```java
if (tradeEvent.isDecrease() && lotCount == 0) {
    throw new IllegalStateException("Cannot process DECREASE: position has no open lots");
}
```

### Solution
```java
@Service
public class TradeValidationService {
    public void validateTrade(TradeEvent tradeEvent, PositionState state) {
        if (tradeEvent.isDecrease() && state.getOpenLots().isEmpty()) {
            throw new InvalidTradeException("Cannot process DECREASE: position has no open lots");
        }
        // ... other validations
    }
}
```

**Benefits**:
- Centralized validation
- Reusable
- Easier to test

---

## 12. Builder Pattern for Complex Objects 🟢

### Issue
Complex object construction with many parameters.

### Solution
Use Lombok `@Builder` or manual builders:

```java
@Builder
public class EventCreationContext {
    private TradeEvent tradeEvent;
    private long version;
    private LotAllocationResult result;
    private String correlationId;
    // ...
}
```

**Benefits**:
- Clearer intent
- Optional parameters
- Immutability

---

## 13. Extract Constants 🟢

### Issue
Magic strings and numbers scattered throughout code.

### Examples
- `"TRADE_REPORT"`, `"PENDING"`, `"SUBMITTED"`, `"FAILED"`
- Status strings
- Event type strings

### Solution
```java
public class RegulatorySubmissionConstants {
    public static final String SUBMISSION_TYPE_TRADE_REPORT = "TRADE_REPORT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_FAILED = "FAILED";
}
```

Or use enums:
```java
public enum RegulatorySubmissionStatus {
    PENDING, SUBMITTED, ACCEPTED, REJECTED, FAILED
}
```

**Benefits**:
- Type safety
- No typos
- IDE autocomplete
- Refactoring support

---

## 14. Testability Improvements 🔴

### Issue
- Large services are hard to mock
- Tight coupling
- Difficult to test individual components

### Solution
- Extract smaller services (as mentioned in #1)
- Use interfaces for dependencies
- Dependency injection (already done, but can be improved)
- Create test doubles/fakes instead of mocks

---

## Implementation Priority

### Phase 1 (High Impact, Low Risk)
1. ✅ Extract Snapshot Management Service
2. ✅ Extract Event Store Service
3. ✅ Fix field injection in controllers
4. ✅ Centralize ObjectMapper configuration

### Phase 2 (Medium Impact, Medium Risk)
5. Extract Position Lifecycle Service
6. Extract UPI Management Service
7. Replace manual JSON building with domain models
8. Extract validation logic

### Phase 3 (Lower Priority)
9. Implement AOP for error handling
10. Improve logging with structured logging
11. Extract constants to enums/constants classes
12. Add builder patterns where appropriate

---

## Metrics to Track

After refactoring, track:
- **Cyclomatic Complexity**: Should decrease
- **Lines of Code per Method**: Target <50 lines
- **Test Coverage**: Should increase
- **Build Time**: Should remain similar
- **Runtime Performance**: Should remain similar or improve

---

## Conclusion

The codebase is functional but has significant opportunities for improvement. The highest priority refactorings focus on:
1. Breaking down large service classes
2. Eliminating code duplication
3. Improving testability
4. Standardizing error handling

These changes will make the codebase more maintainable, testable, and easier to extend.
