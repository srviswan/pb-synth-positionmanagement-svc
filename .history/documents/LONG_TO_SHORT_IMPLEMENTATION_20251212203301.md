# Long-to-Short Position Transition - Implementation

## Overview

When a position crosses from **long to short** (or **short to long**), the system:
1. **Detects the sign change** (quantity crosses zero)
2. **Closes the old position** (TERMINATED status, old UPI)
3. **Creates a new position** (ACTIVE status, new UPI)
4. **Maintains audit trail** via UPI history

## Implementation Details

### 1. Sign Change Detection

**Location**: `HotpathPositionService.processCurrentDatedTrade()`

```java
// Capture quantity BEFORE trade
BigDecimal quantityBeforeTrade = state.getTotalQty();
boolean wasLong = quantityBeforeTrade.compareTo(BigDecimal.ZERO) > 0;
boolean wasShort = quantityBeforeTrade.compareTo(BigDecimal.ZERO) < 0;

// After lot processing, check for sign change
BigDecimal totalQtyAfterTrade = state.getTotalQty();
boolean isLongAfter = totalQtyAfterTrade.compareTo(BigDecimal.ZERO) > 0;
boolean isShortAfter = totalQtyAfterTrade.compareTo(BigDecimal.ZERO) < 0;

boolean signChanged = (wasLong && isShortAfter) || (wasShort && isLongAfter);
```

### 2. Lot Processing for Sign Changes

#### Long → Short (DECREASE exceeds long quantity)

**Scenario**: Long 100 shares, DECREASE 150 shares

**Process**:
1. `reduceLots()` closes all 100 long shares
2. Returns `remainingQuantity = -50` (negative indicates short)
3. `HotpathPositionService` creates short lot with quantity -50
4. Sign change detection triggers position transition

**Code Flow**:
```java
// In LotLogic.reduceLots()
if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
    // After closing all lots, remaining quantity becomes short
    result.setRemainingQuantity(remainingQty.negate()); // Negative = short
}

// In HotpathPositionService
if (result.getRemainingQuantity() != null && result.getRemainingQuantity().compareTo(BigDecimal.ZERO) < 0) {
    BigDecimal shortQty = result.getRemainingQuantity(); // -50
    lotLogic.addLot(state, shortQty, tradeEvent.getPrice(), tradeEvent.getEffectiveDate());
}
```

#### Short → Long (INCREASE exceeds short quantity)

**Scenario**: Short 50 shares, INCREASE 100 shares

**Process**:
1. `addLot()` adds lot with quantity +100
2. Total quantity becomes +50 (long)
3. Sign change detection triggers position transition

**Code Flow**:
```java
// In HotpathPositionService
if (tradeEvent.isIncrease()) {
    // Add lot normally (can be positive or negative)
    lotLogic.addLot(state, tradeEvent.getQuantity(), price, date);
    // Sign change detection below will handle the transition
}
```

### 3. Position Transition Logic

When sign change is detected:

```java
if (signChanged) {
    // 1. Close old position
    String oldUPI = snapshot.getUti();
    snapshot.setStatus(PositionStatus.TERMINATED);
    
    // Record UPI termination
    upiHistoryService.recordUPIChange(
        positionKey, oldUPI, oldUPI,
        PositionStatus.TERMINATED,
        PositionStatus.ACTIVE,
        "TERMINATED",
        tradeEvent,
        "Position closed due to sign change: LONG -> SHORT");
    
    // 2. Create new position
    String newUPI = tradeEvent.getTradeId();
    snapshot.setUti(newUPI);
    snapshot.setStatus(PositionStatus.ACTIVE);
    
    // Record UPI creation
    upiHistoryService.recordUPIChange(
        positionKey, newUPI, oldUPI,
        PositionStatus.ACTIVE,
        PositionStatus.TERMINATED,
        "CREATED",
        tradeEvent,
        "New position created after sign change: SHORT");
}
```

### 4. Negative Quantity Support

#### TaxLot
- **Updated**: `isClosed()` now checks `remainingQty == 0` (not `<= 0`)
- **Supports**: Negative quantities for short positions

#### PositionState
- **Updated**: `getOpenLots()` filters `remainingQty != 0` (includes negative)
- **Supports**: Negative quantities in lots

#### LotLogic
- **Updated**: `reduceLots()` handles both long and short positions
- **Short position reduction**: Adds to negative quantity (makes it less negative)

### 5. Validation Updates

**TradeValidationService**:
- **Changed**: Removed requirement that quantity must be positive
- **Allows**: Negative quantities (for short positions)
- **Note**: Sign changes are validated at processing level

## Example Scenarios

### Scenario 1: Long → Short

**Initial**:
- Position: Long 100 shares @ $50
- Quantity: +100
- UPI: UPI-1

**Trade**: DECREASE 150 shares @ $55

**Process**:
1. Close 100 long shares @ $55
   - Realized P&L: (55 - 50) * 100 = $500
2. Remaining: 50 shares → becomes short
3. Create short lot: -50 shares @ $55
4. Sign change detected: LONG → SHORT

**Result**:
- Old Position: TERMINATED, UPI-1
- New Position: ACTIVE, UPI-2 (from trade ID)
- Quantity: -50 (short)
- UPI History:
  - UPI-1: TERMINATED (sign change: LONG → SHORT)
  - UPI-2: CREATED (new short position)

### Scenario 2: Short → Long

**Initial**:
- Position: Short 50 shares @ $55
- Quantity: -50
- UPI: UPI-1

**Trade**: INCREASE 100 shares @ $60

**Process**:
1. Add lot: +100 shares @ $60
2. Total quantity: -50 + 100 = +50
3. Sign change detected: SHORT → LONG

**Result**:
- Old Position: TERMINATED, UPI-1
- New Position: ACTIVE, UPI-2
- Quantity: +50 (long)
- UPI History:
  - UPI-1: TERMINATED (sign change: SHORT → LONG)
  - UPI-2: CREATED (new long position)

## Coldpath Support

The `RecalculationService` also needs to handle sign changes during event replay:

1. **Track position direction** during replay
2. **Detect sign changes** when replaying events
3. **Create new UPIs** when sign changes occur
4. **Handle backdated trades** that cause sign changes

## Testing

### Test Cases

1. **Long 100 → DECREASE 50**: Normal decrease, stays long
2. **Long 100 → DECREASE 100**: Closes to flat (TERMINATED)
3. **Long 100 → DECREASE 150**: Closes long, creates short 50
4. **Short 50 → INCREASE 30**: Normal increase, stays short
5. **Short 50 → INCREASE 50**: Closes to flat (TERMINATED)
6. **Short 50 → INCREASE 100**: Closes short, creates long 50

## Status

✅ **Implemented**:
- Sign change detection in `HotpathPositionService`
- Negative quantity support in `TaxLot` and `PositionState`
- Lot reduction with remaining quantity tracking
- UPI transition on sign change
- Validation updated to allow negative quantities

⚠️ **Pending**:
- Coldpath support in `RecalculationService`
- Comprehensive test coverage
- Short position P&L calculation verification
- Documentation updates

## Next Steps

1. Add unit tests for sign change scenarios
2. Update `RecalculationService` for coldpath sign changes
3. Verify P&L calculations for short positions
4. Update API documentation
