# Long-to-Short Position Transition Handling

## Overview

When a position crosses from **long to short** (or **short to long**), it represents a fundamental change in position direction. This requires:
1. **Closing the old position** (TERMINATED status)
2. **Creating a new position** with a new UPI
3. **Maintaining audit trail** of the transition

## Business Rules

### Position Direction
- **Long Position**: Quantity > 0 (positive)
- **Short Position**: Quantity < 0 (negative)
- **Flat Position**: Quantity = 0 (TERMINATED)

### Transition Rules

1. **Long → Short**: When a DECREASE trade causes quantity to go from positive to negative
   - Close long position (TERMINATED)
   - Create new short position (new UPI, ACTIVE)

2. **Short → Long**: When an INCREASE trade causes quantity to go from negative to positive
   - Close short position (TERMINATED)
   - Create new long position (new UPI, ACTIVE)

3. **Long → Flat → Short**: When a DECREASE trade causes quantity to go to zero, then another DECREASE creates short
   - First DECREASE: Close long position (TERMINATED)
   - Second DECREASE: Create new short position (NEW_TRADE, new UPI)

4. **Short → Flat → Long**: When an INCREASE trade causes quantity to go to zero, then another INCREASE creates long
   - First INCREASE: Close short position (TERMINATED)
   - Second INCREASE: Create new long position (NEW_TRADE, new UPI)

## Implementation Strategy

### Detection Logic

```java
// Check if quantity sign changed
BigDecimal currentQty = state.getTotalQty();
BigDecimal newQty = currentQty.add(tradeQuantity);

boolean signChanged = (currentQty.compareTo(BigDecimal.ZERO) > 0 && newQty.compareTo(BigDecimal.ZERO) < 0) ||
                      (currentQty.compareTo(BigDecimal.ZERO) < 0 && newQty.compareTo(BigDecimal.ZERO) > 0);
```

### Processing Flow

1. **Apply Trade to Position State**
   - Calculate new quantity after trade
   - Detect sign change

2. **If Sign Changed**:
   - Close old position:
     - Set status to TERMINATED
     - Record UPI termination
     - Save event for old position closure
   - Create new position:
     - Generate new UPI
     - Set status to ACTIVE
     - Create new snapshot
     - Record UPI creation
     - Save event for new position

3. **If No Sign Change**:
   - Continue normal processing
   - Update existing position

## State Machine Updates

### New States

The state machine needs to track position direction:
- `ACTIVE_LONG`: Quantity > 0
- `ACTIVE_SHORT`: Quantity < 0
- `TERMINATED`: Quantity = 0

### Transition Rules

```
ACTIVE_LONG + DECREASE → quantity < 0:
  → TERMINATED (close long) + NEW_TRADE (create short)

ACTIVE_SHORT + INCREASE → quantity > 0:
  → TERMINATED (close short) + NEW_TRADE (create long)
```

## Code Changes Required

### 1. Update PositionStateMachine

Add direction awareness:
- Detect sign changes
- Return special transition result for sign changes

### 2. Update HotpathPositionService

Add sign change detection:
- Check quantity before and after trade
- Handle transition: close old, create new

### 3. Update RecalculationService

Handle sign changes during replay:
- Track position direction during event replay
- Create new position when sign changes detected

### 4. Update Validation

Allow negative quantities but validate:
- DECREASE on long position cannot exceed long quantity (would create short)
- INCREASE on short position cannot exceed short quantity (would create long)
- Or: Allow sign change but treat as position closure + new position

## Example Scenarios

### Scenario 1: Long to Short

**Initial State:**
- Position: Long 100 shares @ $50
- Quantity: +100

**Trade:**
- DECREASE 150 shares @ $55

**Result:**
1. Close long position: 100 shares closed @ $55
   - Realized P&L: (55 - 50) * 100 = $500
   - Status: TERMINATED
   - UPI-1 terminated
2. Create short position: 50 shares short @ $55
   - Status: ACTIVE
   - New UPI-2 created
   - Quantity: -50

### Scenario 2: Short to Long

**Initial State:**
- Position: Short 50 shares @ $55
- Quantity: -50

**Trade:**
- INCREASE 100 shares @ $60

**Result:**
1. Close short position: 50 shares closed @ $60
   - Realized P&L: (55 - 60) * 50 = -$250 (loss on short)
   - Status: TERMINATED
   - UPI-1 terminated
2. Create long position: 50 shares long @ $60
   - Status: ACTIVE
   - New UPI-2 created
   - Quantity: +50

## UPI Management

### UPI Lifecycle

1. **Long Position (UPI-1)**: Created with first long trade
2. **Transition**: Long → Short
   - UPI-1: TERMINATED (long closed)
   - UPI-2: CREATED (short opened)
3. **Transition**: Short → Long
   - UPI-2: TERMINATED (short closed)
   - UPI-3: CREATED (long reopened)

### UPI History Records

- `TERMINATED`: When position closes due to sign change
- `CREATED`: When new position created after sign change
- `SIGN_CHANGE`: New change type to track direction changes

## Event Store

### Events Generated

1. **Position Closure Event**:
   - `event_type`: `POSITION_CLOSED`
   - `reason`: `SIGN_CHANGE_LONG_TO_SHORT` or `SIGN_CHANGE_SHORT_TO_LONG`
   - Contains final P&L for closed position

2. **New Position Event**:
   - `event_type`: `NEW_TRADE`
   - `position_key`: Same (position key doesn't change)
   - `upi`: New UPI
   - Contains initial trade for new position

## Snapshot Management

### Old Position Snapshot
- Status: TERMINATED
- Quantity: 0 (after closing)
- UPI: Old UPI
- Final P&L recorded

### New Position Snapshot
- Status: ACTIVE
- Quantity: New quantity (opposite sign)
- UPI: New UPI
- Initial lot created

## Regulatory Implications

### Trade Reporting

When position transitions:
1. Report closure of old position
2. Report creation of new position
3. Both events linked via correlation ID

### UPI Reporting

- Old UPI: Reported as TERMINATED
- New UPI: Reported as CREATED
- Link between UPIs via `sign_change` reason

## Testing Scenarios

1. **Long 100 → DECREASE 50**: Normal decrease, stays long
2. **Long 100 → DECREASE 100**: Closes to flat (TERMINATED)
3. **Long 100 → DECREASE 150**: Closes long, creates short 50
4. **Short 50 → INCREASE 30**: Normal increase, stays short
5. **Short 50 → INCREASE 50**: Closes to flat (TERMINATED)
6. **Short 50 → INCREASE 100**: Closes short, creates long 50

## Implementation Priority

1. **Phase 1**: Detection and logging
   - Detect sign changes
   - Log transition events
   - Validate behavior

2. **Phase 2**: Position closure
   - Close old position properly
   - Record UPI termination
   - Calculate final P&L

3. **Phase 3**: New position creation
   - Create new position with new UPI
   - Record UPI creation
   - Link to old position

4. **Phase 4**: Coldpath support
   - Handle sign changes during replay
   - Correct UPI history
   - Handle backdated trades that cause sign changes
