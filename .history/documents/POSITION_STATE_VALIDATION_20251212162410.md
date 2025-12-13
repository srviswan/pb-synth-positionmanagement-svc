# Position State Validation Fix

## Problem
The system was allowing `INCREASE` and `DECREASE` trades on **new positions** (positions that don't exist yet). This is invalid because:
- You cannot increase/decrease a position that doesn't exist
- Only `NEW_TRADE` should be allowed for new positions
- This could lead to incorrect state and business logic errors

## Solution
Added validation in `TradeValidationService` to check if a position exists before allowing `INCREASE` or `DECREASE` trades.

## Changes Made

### 1. Added SnapshotRepository Dependency (TradeValidationService.java, lines 23-27)

```java
private final SnapshotRepository snapshotRepository;

public TradeValidationService(SnapshotRepository snapshotRepository) {
    this.snapshotRepository = snapshotRepository;
}
```

### 2. Added Position State Validation (TradeValidationService.java, lines 83-98)

```java
// Position state validation: INCREASE/DECREASE require existing position
if (trade.getPositionKey() != null && trade.getTradeType() != null) {
    boolean positionExists = snapshotRepository.existsById(trade.getPositionKey());
    
    if ("INCREASE".equals(trade.getTradeType()) || "DECREASE".equals(trade.getTradeType())) {
        if (!positionExists) {
            errors.add(String.format(
                "Cannot process %s trade on new position. Position '%s' does not exist. Use NEW_TRADE to create a new position first.",
                trade.getTradeType(), trade.getPositionKey()));
        }
    } else if ("NEW_TRADE".equals(trade.getTradeType()) && positionExists) {
        // Optional: Warn if NEW_TRADE is used on existing position (might be intentional for reset)
        log.debug("NEW_TRADE used on existing position {} - this will create a new position state", 
                trade.getPositionKey());
    }
}
```

## Validation Rules

### ✅ Allowed Operations

1. **NEW_TRADE on new position** ✅
   - Creates a new position
   - No existing position required

2. **NEW_TRADE on existing position** ⚠️
   - Allowed (may be intentional for reset)
   - Logs debug message

3. **INCREASE on existing position** ✅
   - Increases existing position
   - Position must exist

4. **DECREASE on existing position** ✅
   - Decreases existing position
   - Position must exist

### ❌ Rejected Operations

1. **INCREASE on new position** ❌
   - **Validation Error**: "Cannot process INCREASE trade on new position. Position 'XXX' does not exist. Use NEW_TRADE to create a new position first."
   - Trade is rejected and sent to DLQ

2. **DECREASE on new position** ❌
   - **Validation Error**: "Cannot process DECREASE trade on new position. Position 'XXX' does not exist. Use NEW_TRADE to create a new position first."
   - Trade is rejected and sent to DLQ

## Flow

```
Trade Arrives
    ↓
TradeValidationService.validate()
    ↓
Check: Is trade type INCREASE or DECREASE?
    ↓
    YES → Check: Does position exist?
    │       ↓
    │       NO → ❌ Validation Error → DLQ
    │       YES → ✅ Continue processing
    ↓
    NO (NEW_TRADE) → ✅ Continue processing
```

## Example Scenarios

### Scenario 1: Valid Flow
```
1. NEW_TRADE on position "POS-001" → ✅ Creates position
2. INCREASE on position "POS-001" → ✅ Increases position
3. DECREASE on position "POS-001" → ✅ Decreases position
```

### Scenario 2: Invalid Flow (Now Rejected)
```
1. INCREASE on position "POS-002" → ❌ Validation Error: Position doesn't exist
   → Trade sent to DLQ
   → Error logged
```

### Scenario 3: Invalid Flow (Now Rejected)
```
1. DECREASE on position "POS-003" → ❌ Validation Error: Position doesn't exist
   → Trade sent to DLQ
   → Error logged
```

## Benefits

### ✅ Early Validation
- Catches invalid trades **before** they enter the processing pipeline
- Prevents unnecessary processing of invalid trades
- Reduces error handling complexity in business logic

### ✅ Clear Error Messages
- Provides specific error message explaining the issue
- Suggests correct action (use NEW_TRADE first)
- Helps with debugging and troubleshooting

### ✅ Data Quality
- Prevents invalid state transitions
- Maintains data integrity
- Ensures business rules are enforced

### ✅ DLQ Routing
- Invalid trades are sent to Dead Letter Queue
- Allows manual review and correction
- Prevents data loss

## Testing

To verify the fix works:

1. **Test INCREASE on new position**
   ```bash
   curl -X POST http://localhost:8081/api/trades \
     -H "Content-Type: application/json" \
     -d '{
       "tradeId": "T-INVALID-001",
       "positionKey": "POS-NEW-001",
       "tradeType": "INCREASE",
       "quantity": 100,
       "price": 50.00,
       "effectiveDate": "2025-12-12"
     }'
   ```
   **Expected**: Validation error, trade rejected

2. **Test DECREASE on new position**
   ```bash
   curl -X POST http://localhost:8081/api/trades \
     -H "Content-Type: application/json" \
     -d '{
       "tradeId": "T-INVALID-002",
       "positionKey": "POS-NEW-002",
       "tradeType": "DECREASE",
       "quantity": 100,
       "price": 50.00,
       "effectiveDate": "2025-12-12"
     }'
   ```
   **Expected**: Validation error, trade rejected

3. **Test valid flow**
   ```bash
   # Step 1: Create position
   curl -X POST http://localhost:8081/api/trades \
     -H "Content-Type: application/json" \
     -d '{
       "tradeId": "T-VALID-001",
       "positionKey": "POS-VALID-001",
       "tradeType": "NEW_TRADE",
       "quantity": 1000,
       "price": 50.00,
       "effectiveDate": "2025-12-12"
     }'
   # Expected: ✅ Success
   
   # Step 2: Increase position
   curl -X POST http://localhost:8081/api/trades \
     -H "Content-Type: application/json" \
     -d '{
       "tradeId": "T-VALID-002",
       "positionKey": "POS-VALID-001",
       "tradeType": "INCREASE",
       "quantity": 500,
       "price": 55.00,
       "effectiveDate": "2025-12-12"
     }'
   # Expected: ✅ Success
   ```

## Summary

- ✅ **INCREASE/DECREASE on new position**: Now **rejected** with clear error message
- ✅ **NEW_TRADE on new position**: Still **allowed** (creates position)
- ✅ **INCREASE/DECREASE on existing position**: Still **allowed** (updates position)
- ✅ **Early validation**: Catches errors before processing
- ✅ **DLQ routing**: Invalid trades sent to Dead Letter Queue

The validation ensures proper state transitions and prevents invalid operations on new positions.
