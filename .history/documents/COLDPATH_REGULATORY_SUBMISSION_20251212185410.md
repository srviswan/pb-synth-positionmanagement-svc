# Coldpath Regulatory Submission

## Overview

The coldpath (backdated trade processing) now includes comprehensive regulatory submission:
1. **All backdated trades are submitted to regulators** (like hotpath trades)
2. **UPI invalidation events** are published when UPI-2 is invalidated
3. **Individual trade correction events** are published for each affected trade

## Flow

### 1. Backdated Trade Processing (Coldpath)

When a backdated trade is processed:

1. **Event Saved**: Backdated trade event is persisted to the event store
2. **Event Stream Replay**: All events are replayed chronologically with the backdated trade
3. **Snapshot Recalculated**: Position snapshot is recalculated with correct UPI and status
4. **Backdated Trade Submission**: Trade is submitted to regulator (same as hotpath)
5. **UPI Invalidation Check**: If UPI changed, invalidation events are published
6. **Trade Corrections**: Individual correction events for each affected trade

### 2. Regulatory Submission for Backdated Trades

The `RegulatorySubmissionService.submitTradeToRegulator()` is called for backdated trades:

- **Event Type**: `TRADE_REPORT`
- **Same Structure**: Identical to hotpath trade reports
- **Includes UPI**: Uses the corrected UPI from the recalculated snapshot
- **Published To**: `regulatory-events` Kafka topic

### 3. UPI Invalidation Events

When UPI-2 is invalidated (reverted to UPI-1), two types of events are published:

#### 3a. UPI Invalidation Summary Event

**Event Type**: `UPI_INVALIDATION`

**Purpose**: Provides a summary of the UPI change and lists all affected trades.

**Payload**:
```json
{
  "type": "UPI_INVALIDATION",
  "positionKey": "POS-XXX",
  "invalidatedUPI": "T-UPI-002-XXX",
  "newUPI": "T-UPI-001-XXX",
  "invalidatedTradeIds": ["T-UPI-002-XXX", "T-UPI-003-XXX"],
  "reason": "BACKDATED_TRADE_RECALCULATION",
  "backdatedTradeId": "T-BACKDATED-001",
  "effectiveDate": "2025-12-09",
  "occurredAt": "2025-12-12T18:30:00Z",
  "actionRequired": "RESUBMIT_TRADES_WITH_NEW_UPI",
  "message": "Trades submitted with UPI T-UPI-002-XXX are invalid. These trades must be re-submitted with UPI T-UPI-001-XXX."
}
```

#### 3b. Trade Correction Events (One per Affected Trade)

**Event Type**: `TRADE_CORRECTION`

**Purpose**: Provides detailed correction instructions for each individual trade.

**Payload** (per trade):
```json
{
  "type": "TRADE_CORRECTION",
  "tradeId": "T-UPI-002-XXX",
  "positionKey": "POS-XXX",
  "originalUPI": "T-UPI-002-XXX",
  "correctedUPI": "T-UPI-001-XXX",
  "tradeType": "NEW_TRADE",
  "quantity": 500,
  "price": 60.00,
  "effectiveDate": "2025-12-12",
  "reason": "UPI_INVALIDATION",
  "backdatedTradeId": "T-BACKDATED-001",
  "occurredAt": "2025-12-12T18:30:00Z",
  "actionRequired": "CORRECT_TRADE_WITH_NEW_UPI",
  "message": "Trade T-UPI-002-XXX was submitted with UPI T-UPI-002-XXX which is now invalid. This trade must be corrected with UPI T-UPI-001-XXX."
}
```

## Implementation Details

### Files Modified

1. **`RecalculationService.java`**
   - Added `RegulatorySubmissionService` dependency
   - Added `submitTradeToRegulator()` call for backdated trades
   - Added `publishTradeCorrectionEvents()` method
   - Enhanced UPI invalidation handling

### Integration Points

In `RecalculationService.recalculatePosition()`:

```java
// 9. Submit backdated trade to regulator (coldpath)
regulatorySubmissionService.submitTradeToRegulator(backdatedTrade, correctedSnapshot);

// 10. Check if UPI changed (UPI-2 invalidated)
if (UPI changed) {
    // 10a. Find affected trades
    List<String> invalidatedTradeIds = findTradesForUPI(...);
    
    // 10b. Publish UPI invalidation summary event
    publishUPIInvalidationEvent(...);
    
    // 10c. Publish individual correction events for each trade
    publishTradeCorrectionEvents(...);
}
```

## Event Types Published

### Coldpath Events

1. **TRADE_REPORT**: For the backdated trade itself
   - Published once per backdated trade
   - Same structure as hotpath trade reports

2. **UPI_INVALIDATION**: Summary of UPI change
   - Published once when UPI-2 is invalidated
   - Lists all affected trade IDs

3. **TRADE_CORRECTION**: Individual trade corrections
   - Published once per affected trade
   - Contains detailed trade information and correction instructions

## Benefits

1. **Complete Regulatory Coverage**: All trades (hotpath and coldpath) are submitted to regulators
2. **Detailed Corrections**: Individual correction events provide precise instructions for each trade
3. **Summary View**: UPI invalidation event provides overview of the change
4. **Traceability**: Each correction event links back to the backdated trade that caused it
5. **Compliance**: Ensures regulators are notified of all position changes and corrections

## Regulatory Workflow

1. **Backdated Trade Processed**: Trade is recalculated and position is corrected
2. **Trade Submitted**: Backdated trade is submitted to regulator (TRADE_REPORT)
3. **UPI Change Detected**: System detects UPI-2 was invalidated
4. **Summary Published**: UPI_INVALIDATION event published with list of affected trades
5. **Corrections Published**: TRADE_CORRECTION event published for each affected trade
6. **Regulator Processes**:
   - Acknowledges backdated trade submission
   - Processes UPI invalidation summary
   - Processes individual trade corrections
   - Updates regulatory records
   - Triggers re-submission workflow

## Error Handling

- **Non-blocking**: Regulatory submission failures don't block recalculation
- **Individual Trade Handling**: If one trade correction fails, others continue
- **Logging**: All errors are logged for audit and debugging
- **Status Tracking**: Submission status tracked in `RegulatorySubmissionEntity`

## Comparison: Hotpath vs Coldpath

| Aspect | Hotpath | Coldpath |
|--------|---------|----------|
| **Trade Submission** | ✅ TRADE_REPORT | ✅ TRADE_REPORT |
| **UPI Invalidation** | ❌ N/A | ✅ UPI_INVALIDATION |
| **Trade Corrections** | ❌ N/A | ✅ TRADE_CORRECTION (per trade) |
| **Timing** | Immediate | After recalculation |
| **UPI Used** | Current UPI | Corrected UPI (from replay) |

## Future Enhancements

1. **Batch Corrections**: Support for batch correction event publishing
2. **Correction Status Tracking**: Track status of each correction event
3. **Automated Re-submission**: Automatically trigger re-submission of corrected trades
4. **Compliance Dashboard**: Visualize all corrections and their status
5. **Regulatory Response Handling**: Process regulator acknowledgments for corrections
