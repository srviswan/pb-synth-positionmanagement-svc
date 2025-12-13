# UPI Management with Backdated Trades

## Problem Statement

When processing backdated trades, we need to handle UPI (Unique Position Identifier) management correctly:

1. **Scenario 1**: Position with UPI-1 → Closed (TERMINATED) → New trade creates UPI-2 → Backdated trade comes in before UPI-1 termination
2. **Scenario 2**: Backdated trade comes in that impacts UPI-2 position → Need to terminate UPI-2 and restore UPI-1

## Solution

### Implementation

The solution tracks UPI and position status during event replay in `RecalculationService`:

1. **UPI Tracking During Replay** (`replayEventsWithUPITracking`):
   - Tracks current UPI as events are replayed chronologically
   - When a `NEW_TRADE` event is encountered:
     - If position was TERMINATED (qty = 0), this is a reopening → set new UPI
     - If position was ACTIVE, keep existing UPI (shouldn't happen in normal flow)
   - When position qty goes to 0, set status to TERMINATED
   - When position qty goes from 0 to >0 (via backdated trade), reopen with original UPI

2. **Corrected Snapshot Creation** (`createCorrectedSnapshot`):
   - Uses UPI and status from replay result (determined chronologically)
   - If backdated trade reopens a TERMINATED position, restores the original UPI
   - If backdated trade affects a newer position (UPI-2), the replay will show the correct UPI based on chronological order

### Key Logic

```java
// During replay:
if (event.getEventType() == EventType.NEW_TRADE) {
    if (state.getTotalQty() == 0 && currentStatus == TERMINATED) {
        // Position was closed, this is a reopening with new UPI
        currentUPI = tradeEvent.getTradeId();
        currentStatus = ACTIVE;
    } else if (currentUPI == null) {
        // First NEW_TRADE, set initial UPI
        currentUPI = tradeEvent.getTradeId();
        currentStatus = ACTIVE;
    }
}

// Check if position should be TERMINATED
if (state.getTotalQty() == 0 && currentStatus == ACTIVE) {
    currentStatus = TERMINATED;
}
```

### Scenarios Handled

#### Scenario 1: Backdated Trade Before UPI-1 Termination
- **Timeline**: UPI-1 created → UPI-1 closed → UPI-2 created → Backdated trade (before UPI-1 closure)
- **Result**: Backdated trade is inserted chronologically, affecting UPI-1. UPI-1 remains active, UPI-2 is invalidated.

#### Scenario 2: Backdated Trade Affects UPI-2
- **Timeline**: UPI-1 created → UPI-1 closed → UPI-2 created → Backdated trade (affects closure)
- **Result**: Backdated trade prevents UPI-1 closure, so UPI-2 should not exist. Replay shows UPI-1 is still active.

## Testing

Run the test script:
```bash
/tmp/test_upi_backdated.sh
```

This tests both scenarios and verifies:
- UPI is correctly restored when backdated trades reopen positions
- Status transitions (ACTIVE ↔ TERMINATED) are handled correctly
- Chronological replay determines the correct UPI

## Benefits

1. **Chronological Correctness**: UPI is determined by replaying events in chronological order
2. **Automatic Correction**: Backdated trades automatically correct UPI and status
3. **No Manual Intervention**: System handles UPI restoration automatically
4. **Audit Trail**: Event store maintains complete history for debugging

## Future Enhancements

1. **UPI History Table**: Track all UPI changes for audit purposes
2. **UPI Merge Detection**: Detect when backdated trades merge separate positions
3. **Notification**: Notify downstream systems when UPI changes due to backdated trades
