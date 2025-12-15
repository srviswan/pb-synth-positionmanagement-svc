# Position Key Generation - Deterministic Design

## Overview

The `position_key` is a **deterministic hash** generated from account, instrument, and currency. This ensures:
- Same position always produces the same key
- Consistent partitioning (Kafka and database)
- No collisions (with proper hash algorithm)
- Reproducible across systems

## Current State

### Documentation Says

From schema and code comments:
- Schema: `position_key VARCHAR(64) NOT NULL, -- Hash(Acct+Instr+Ccy)`
- TradeEvent: `private String positionKey; // Hash(Account+Instrument+Currency)`

### Implementation Gap

**Currently**: `position_key` is **passed in** as part of `TradeEvent` - no generation logic exists in the codebase.

**Issue**: This means:
- Upstream systems must generate the key
- No guarantee of consistent format
- Risk of collisions or inconsistencies
- No validation of key format

## Recommended Implementation

### PositionKeyGenerator Utility

Created `PositionKeyGenerator` utility class that provides:

1. **Deterministic Generation**:
   ```java
   String positionKey = generator.generatePositionKey("ACC001", "AAPL", "USD");
   // Always produces the same key for same inputs
   ```

2. **Hash Algorithm**: SHA-256
   - Produces 64-character hex string
   - Collision-resistant
   - Deterministic (same input = same output)

3. **Input Normalization**:
   - Trims whitespace
   - Converts to uppercase
   - Ensures consistency

4. **Format**: `"ACCOUNT|INSTRUMENT|CURRENCY"`
   - Pipe separator for clarity
   - Easy to parse if needed
   - Deterministic ordering

## Implementation Details

### Generation Algorithm

```java
public String generatePositionKey(String account, String instrument, String currency) {
    // 1. Normalize inputs
    String normalizedAccount = account.trim().toUpperCase();
    String normalizedInstrument = instrument.trim().toUpperCase();
    String normalizedCurrency = currency.trim().toUpperCase();
    
    // 2. Create composite string
    String input = String.format("%s|%s|%s", 
        normalizedAccount, normalizedInstrument, normalizedCurrency);
    
    // 3. Generate SHA-256 hash
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
    
    // 4. Convert to hex string (64 characters)
    return bytesToHex(hashBytes);
}
```

### Example Output

```java
Input:  account="ACC001", instrument="AAPL", currency="USD"
Output: "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2"
        (64 hex characters)
```

### Deterministic Properties

✅ **Same Input → Same Output**:
```java
generatePositionKey("ACC001", "AAPL", "USD") 
  → "a1b2c3d4..." (always the same)

generatePositionKey("ACC001", "AAPL", "USD") 
  → "a1b2c3d4..." (same result)
```

✅ **Case Insensitive**:
```java
generatePositionKey("acc001", "aapl", "usd") 
  → "a1b2c3d4..." (normalized to uppercase)

generatePositionKey("ACC001", "AAPL", "USD") 
  → "a1b2c3d4..." (same result)
```

✅ **Whitespace Tolerant**:
```java
generatePositionKey(" ACC001 ", " AAPL ", " USD ") 
  → "a1b2c3d4..." (trimmed)

generatePositionKey("ACC001", "AAPL", "USD") 
  → "a1b2c3d4..." (same result)
```

## Integration Points

### Option 1: Generate in TradeEvent Builder

Add a builder method that generates position_key:

```java
TradeEvent.builder()
    .account("ACC001")
    .instrument("AAPL")
    .currency("USD")
    .build(); // Automatically generates position_key
```

### Option 2: Generate in TradeController

Generate position_key when receiving trade:

```java
@PostMapping
public ResponseEntity<Map<String, Object>> submitTrade(@RequestBody TradeEvent tradeEvent) {
    // Generate position_key if not provided
    if (tradeEvent.getPositionKey() == null) {
        String positionKey = positionKeyGenerator.generatePositionKey(
            tradeEvent.getAccount(),
            tradeEvent.getInstrument(),
            tradeEvent.getCurrency()
        );
        tradeEvent.setPositionKey(positionKey);
    }
    // ... process trade
}
```

### Option 3: Validation Service

Validate and optionally generate position_key:

```java
public ValidationResult validate(TradeEvent trade) {
    // If position_key provided, validate format
    if (trade.getPositionKey() != null) {
        if (!positionKeyGenerator.isValidPositionKeyFormat(trade.getPositionKey())) {
            errors.add("Invalid position key format");
        }
    } else {
        // Generate if account/instrument/currency provided
        if (trade.getAccount() != null && trade.getInstrument() != null && trade.getCurrency() != null) {
            trade.setPositionKey(positionKeyGenerator.generatePositionKey(
                trade.getAccount(), trade.getInstrument(), trade.getCurrency()));
        }
    }
}
```

## TradeEvent Model Enhancement

### Current Model

```java
public class TradeEvent {
    private String tradeId;
    private String positionKey; // Hash(Account+Instrument+Currency)
    // ... other fields
}
```

### Enhanced Model (Recommended)

```java
public class TradeEvent {
    private String tradeId;
    
    // Source fields (for position key generation)
    private String account;
    private String instrument;
    private String currency;
    
    // Generated field
    private String positionKey; // Hash(Account+Instrument+Currency)
    
    // ... other fields
    
    /**
     * Generate position key from account/instrument/currency if not set
     */
    public void ensurePositionKey(PositionKeyGenerator generator) {
        if (this.positionKey == null && account != null && instrument != null && currency != null) {
            this.positionKey = generator.generatePositionKey(account, instrument, currency);
        }
    }
}
```

## Partition Alignment

### Database Partitioning

#### PostgreSQL (Native Hash Partitioning)
PostgreSQL uses hash partitioning:
```sql
PARTITION BY HASH (position_key)
```

Partition assignment:
```sql
partition_number = hashtext(position_key) % 16
```

#### MS SQL Server (Computed Column + Range Partitioning)
SQL Server requires a workaround:
```sql
-- Computed column with hash
partition_hash AS (ABS(HASHBYTES('SHA2_256', position_key)) % 16) PERSISTED

-- Range partitioning on computed column
CREATE PARTITION FUNCTION pf_event_store_hash (INT)
AS RANGE LEFT FOR VALUES (0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15);
```

**Note**: SQL Server's `HASHBYTES('SHA2_256', ...)` may produce different results than Java's SHA-256, but modulo ensures even distribution.

See `SQL_SERVER_PARTITIONING.md` for complete implementation details.

### Kafka Partitioning

Kafka uses message key for partitioning:
```java
kafkaTemplate.send("trade-events", positionKey, message);
```

Partition assignment:
```
partition_number = hash(positionKey) % num_partitions
```

### Alignment Strategy

**Ideal**: Use same hash function for both Kafka and database partitioning.

**Current**: 
- Database (PostgreSQL): `hashtext()` function
- Database (SQL Server): `HASHBYTES('SHA2_256', ...)` in computed column
- Kafka: Default partitioner (murmur2 hash)

**Recommendation**: 
- Use deterministic position_key generation (SHA-256)
- Both systems will hash the same key
- Alignment is approximate but good enough for distribution
- For SQL Server, let the database compute `partition_hash` using computed column

## Benefits of Deterministic Generation

### 1. **Consistency**
- Same account/instrument/currency always produces same key
- Reproducible across systems
- No ambiguity

### 2. **Partitioning Alignment**
- Same position always maps to same partitions
- Predictable distribution
- Better cache locality

### 3. **Validation**
- Can validate key format
- Detect malformed keys
- Ensure proper length/format

### 4. **Debugging**
- Can regenerate key from source fields
- Easier troubleshooting
- Better observability

### 5. **Security**
- One-way hash (cannot reverse)
- No sensitive data in key
- Collision-resistant

## Usage Examples

### Example 1: Generate from Trade Fields

```java
@Autowired
private PositionKeyGenerator positionKeyGenerator;

public void processTrade(TradeEvent tradeEvent) {
    // Generate position key if not provided
    if (tradeEvent.getPositionKey() == null) {
        String positionKey = positionKeyGenerator.generatePositionKey(
            tradeEvent.getAccount(),
            tradeEvent.getInstrument(),
            tradeEvent.getCurrency()
        );
        tradeEvent.setPositionKey(positionKey);
    }
    
    // Process trade...
}
```

### Example 2: Validate Existing Key

```java
public ValidationResult validate(TradeEvent trade) {
    if (trade.getPositionKey() != null) {
        if (!positionKeyGenerator.isValidPositionKeyFormat(trade.getPositionKey())) {
            errors.add("Invalid position key format. Expected 64 hex characters.");
        }
    }
    // ...
}
```

### Example 3: Get Partition Number

```java
String positionKey = tradeEvent.getPositionKey();
int partition = positionKeyGenerator.getPartitionNumber(positionKey, 16);
log.info("Position {} will be stored in partition {}", positionKey, partition);
```

## Migration Strategy

### Phase 1: Add Generator Utility
- ✅ Create `PositionKeyGenerator` class
- ✅ Add unit tests
- ✅ Document usage

### Phase 2: Enhance TradeEvent Model
- Add `account`, `instrument`, `currency` fields
- Add `ensurePositionKey()` method
- Update builders

### Phase 3: Integrate into Services
- Update `TradeController` to generate keys
- Update `TradeValidationService` to validate/generate
- Update tests

### Phase 4: Backward Compatibility
- Support both generated and provided keys
- Validate provided keys match expected format
- Log warnings for manually provided keys

## Testing

### Unit Tests

```java
@Test
void testGeneratePositionKey_Deterministic() {
    String key1 = generator.generatePositionKey("ACC001", "AAPL", "USD");
    String key2 = generator.generatePositionKey("ACC001", "AAPL", "USD");
    assertEquals(key1, key2); // Same input = same output
}

@Test
void testGeneratePositionKey_CaseInsensitive() {
    String key1 = generator.generatePositionKey("acc001", "aapl", "usd");
    String key2 = generator.generatePositionKey("ACC001", "AAPL", "USD");
    assertEquals(key1, key2); // Case-insensitive
}
```

## Summary

**Current State**: 
- ❌ No deterministic generation in codebase
- ❌ Position key passed in from upstream
- ⚠️ Risk of inconsistencies

**Recommended State**:
- ✅ `PositionKeyGenerator` utility class
- ✅ SHA-256 hash for deterministic generation
- ✅ Input normalization (case, whitespace)
- ✅ Format validation
- ✅ Partition number calculation

**Benefits**:
- Deterministic and reproducible
- Consistent partitioning
- Better validation
- Easier debugging
- Security (one-way hash)

The `PositionKeyGenerator` class has been created and tested. Integration into the trade processing flow is the next step.
