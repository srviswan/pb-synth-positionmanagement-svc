# New Position Key on Direction Change - Implementation

## Summary

When a long position moves to short (or vice versa), the system now creates a **NEW position_key** with the opposite direction. This simplifies the codebase by eliminating complex sign change detection and UPI transition logic.

## Implementation Details

### 1. PositionKeyGenerator Updates

**Added direction parameter**:
```java
public String generatePositionKey(String account, String instrument, String currency, boolean isShort)
```

**Hash includes direction**:
```java
position_key = Hash(Account + Instrument + Currency + Direction)
// Where Direction = "LONG" or "SHORT"
```

**Methods**:
- `generatePositionKey(account, instrument, currency)` - Defaults to LONG (backward compatible)
- `generatePositionKey(account, instrument, currency, isShort)` - Explicit direction
- `generatePositionKeyForOppositeDirection(...)` - Helper for sign changes

### 2. TradeEvent Updates

**Added fields** (optional for backward compatibility):
```java
private String account;      // Account identifier
private String instrument;  // Instrument identifier
private String currency;     // Currency code
```

**Required for sign change transitions**: When a sign change is detected, these fields must be present to generate the new position_key.

### 3. HotpathPositionService Updates

**Sign Change Detection**:
- Detects when quantity sign changes (long → short or short → long)
- Validates account/instrument/currency are present
- Generates new position_key with opposite direction

**Processing Flow**:
1. **Save event for old position** (the DECREASE/INCREASE that triggered sign change)
2. **Close old position** (TERMINATED status, old position_key)
3. **Generate new position_key** with opposite direction
4. **Create new position** (ACTIVE status, new position_key, new UPI)
5. **Save event for new position** (NEW_TRADE on new position_key)

**Key Code**:
```java
if (signChanged) {
    // Generate new position_key
    String newPositionKey = positionKeyGenerator.generatePositionKey(
        tradeEvent.getAccount(),
        tradeEvent.getInstrument(),
        tradeEvent.getCurrency(),
        isShortAfter);
    
    // Close old position
    snapshot.setStatus(TERMINATED);
    snapshotRepository.saveAndFlush(snapshot);
    
    // Create new position
    SnapshotEntity newSnapshot = snapshotService.createNewSnapshot(newPositionKey, tradeEvent);
    // ... process new position
}
```

### 4. Benefits

✅ **Simpler Code**: No complex UPI transition logic  
✅ **Natural Separation**: Long and short are different positions (different keys)  
✅ **Better Partitioning**: Long and short naturally separated across partitions  
✅ **Cleaner Logic**: Each position_key has single direction  
✅ **Simpler UPI Management**: New position_key = new UPI automatically  

## Usage

### For New Trades

```java
TradeEvent trade = TradeEvent.builder()
    .tradeId("T001")
    .account("ACC001")
    .instrument("AAPL")
    .currency("USD")
    .positionKey(positionKeyGenerator.generatePositionKey("ACC001", "AAPL", "USD", false)) // LONG
    .tradeType("NEW_TRADE")
    .quantity(new BigDecimal("100"))
    .price(new BigDecimal("50.00"))
    .build();
```

### For Sign Change Scenarios

**Example: Long 100 → DECREASE 150**

1. Trade arrives with account/instrument/currency
2. System processes DECREASE, closes all 100 long shares
3. Detects sign change: LONG → SHORT
4. Generates new position_key: `Hash("ACC001|AAPL|USD|SHORT")`
5. Closes old position (old position_key, TERMINATED)
6. Creates new position (new position_key, ACTIVE, new UPI)
7. Processes remaining 50 shares as NEW_TRADE on new position_key

## Validation

**Current**: Account/instrument/currency are optional in TradeEvent  
**On Sign Change**: Required - system throws `IllegalStateException` if missing

**Future Enhancement**: Could add validation to require these fields upfront if sign change is possible.

## Testing

✅ **PositionKeyGenerator Tests**: 20 tests passing
- Tests for direction parameter
- Tests for long vs short key generation
- Tests for opposite direction generation

## Migration

**Existing Positions**: 
- Assume LONG direction (or derive from quantity sign)
- Existing position_keys remain valid

**New Positions**: 
- Generate position_key with explicit direction
- Long positions: `generatePositionKey(account, instrument, currency, false)`
- Short positions: `generatePositionKey(account, instrument, currency, true)`

**Sign Change**: 
- Automatically generates new position_key with opposite direction
- Old position_key remains in database (TERMINATED)
- New position_key created (ACTIVE)

## Files Modified

1. ✅ `PositionKeyGenerator.java` - Added direction parameter
2. ✅ `TradeEvent.java` - Added account/instrument/currency fields
3. ✅ `HotpathPositionService.java` - Sign change logic with new position_key
4. ✅ `PositionKeyGeneratorTest.java` - Added direction tests

## Status

✅ **Implementation Complete**
- Position key generation with direction
- Sign change detection and new position_key creation
- Old position closure
- New position creation
- Event saving for both positions
- UPI management

**Ready for Testing**
