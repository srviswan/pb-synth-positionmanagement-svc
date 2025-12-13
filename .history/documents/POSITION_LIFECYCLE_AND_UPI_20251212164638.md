# Position Lifecycle and UPI Management

## Overview
Implemented position lifecycle management with UPI (Unique Position Identifier) tracking:
- Position closure when quantity reaches zero
- Position reopening with new UPI
- Status transitions (ACTIVE ↔ TERMINATED)

## Implementation

### 1. Position Status Management (HotpathPositionService.java, lines 246-261)

#### Position Closure
```java
// Check if position should be closed (qty = 0)
BigDecimal totalQtyAfterTrade = state.getTotalQty();
boolean positionClosed = totalQtyAfterTrade.compareTo(BigDecimal.ZERO) == 0;

if (positionClosed && snapshot.getStatus() == PositionStatus.ACTIVE) {
    snapshot.setStatus(PositionStatus.TERMINATED);
    log.info("Position {} closed: quantity is zero, status set to TERMINATED", positionKey);
}
```

#### Position Reopening
```java
// Check if position is being reopened
boolean isReopening = snapshot.getStatus() == PositionStatus.TERMINATED && 
                      tradeEvent.getTradeType().equals("NEW_TRADE");

if (isReopening) {
    snapshot.setStatus(PositionStatus.ACTIVE);
    snapshot.setUti(tradeEvent.getTradeId()); // New UPI for reopened position
    log.info("Position {} reopened: NEW_TRADE on TERMINATED position, new UPI: {}", 
            positionKey, tradeEvent.getTradeId());
}
```

### 2. UPI Management

#### Initial UPI
- Set to first trade ID when position is created
- Stored in `SnapshotEntity.uti` field

#### UPI on Reopening
- Updated to new trade ID when position is reopened
- Ensures each position lifecycle has a unique identifier

## Test Case: Position Lifecycle

### Test Flow

```
1. NEW_TRADE (1000 @ $50.00)
   → UPI: T-LIFECYCLE-001
   → Status: ACTIVE
   → Qty: 1000

2. INCREASE (500 @ $55.00)
   → UPI: T-LIFECYCLE-001 (unchanged)
   → Status: ACTIVE
   → Qty: 1500

3. Partial DECREASE (300 @ $60.00)
   → UPI: T-LIFECYCLE-001 (unchanged)
   → Status: ACTIVE
   → Qty: 1200
   → P&L: ($60 - $50) × 300 = $3,000 profit

4. Full DECREASE (1200 @ $65.00)
   → UPI: T-LIFECYCLE-001 (unchanged)
   → Status: TERMINATED (qty = 0)
   → Qty: 0
   → P&L: ($65 - $50) × 1200 = $18,000 profit

5. NEW_TRADE (2000 @ $70.00) - Reopen
   → UPI: T-LIFECYCLE-005 (NEW UPI)
   → Status: ACTIVE (reopened)
   → Qty: 2000
```

### Expected Behavior

#### Position Closure
- **Trigger**: When `totalQty == 0` after a DECREASE trade
- **Action**: Set `status = TERMINATED`
- **UPI**: Remains unchanged (preserves original UPI)

#### Position Reopening
- **Trigger**: When `NEW_TRADE` is processed on a `TERMINATED` position
- **Action**: 
  - Set `status = ACTIVE`
  - Update `uti = newTradeId` (new UPI)
- **Result**: Position starts a new lifecycle with new UPI

## Status Transitions

```
ACTIVE → ACTIVE (normal trades)
ACTIVE → TERMINATED (qty = 0)
TERMINATED → ACTIVE (NEW_TRADE on closed position)
```

## UPI Lifecycle

```
Initial: UPI = first trade ID
During Active: UPI unchanged
On Closure: UPI unchanged
On Reopen: UPI = new trade ID
```

## Test Results

### REST API Test
✅ All 5 trades processed successfully:
1. NEW_TRADE: Created position with initial UPI
2. INCREASE: Position remains active, UPI unchanged
3. Partial DECREASE: Position remains active, UPI unchanged
4. Full DECREASE: Position closed (TERMINATED), UPI unchanged
5. NEW_TRADE: Position reopened (ACTIVE), new UPI assigned

### Verification Points
- ✅ Position qty goes to zero after full close
- ✅ Status changes to TERMINATED when qty = 0
- ✅ UPI remains unchanged during closure
- ✅ NEW_TRADE on TERMINATED position reopens it
- ✅ New UPI assigned on reopening
- ✅ Status changes to ACTIVE on reopening

## Code Locations

1. **Status Management**: `HotpathPositionService.processCurrentDatedTrade()` (lines 246-261)
2. **UPI Assignment**: `HotpathPositionService.createNewSnapshot()` (line 330)
3. **UPI Update**: `HotpathPositionService.processCurrentDatedTrade()` (line 258)
4. **Test**: `PositionLifecycleTest.testPositionLifecycle_CloseAndReopen()`

## Summary

- ✅ **Position Closure**: Automatically set to TERMINATED when qty = 0
- ✅ **Position Reopening**: NEW_TRADE on TERMINATED position reopens it
- ✅ **UPI Management**: New UPI assigned when position is reopened
- ✅ **Status Transitions**: ACTIVE ↔ TERMINATED based on quantity
- ✅ **Complete Lifecycle**: Create → Add → Partial Close → Full Close → Reopen

The position lifecycle management is working correctly with proper UPI tracking.
