# UPI Invalidation Regulatory Event

## Overview

When a backdated trade causes UPI-2 to be invalidated (reverted to UPI-1), the system must notify the regulator that trades previously submitted with UPI-2 are now invalid and need to be re-submitted with UPI-1.

## Problem Statement

In a position lifecycle:
1. **UPI-1** is created with the first trade
2. Position is closed (quantity goes to zero) → UPI-1 is TERMINATED
3. A new trade arrives → **UPI-2** is created
4. A backdated trade arrives that affects the position before UPI-1 termination
5. After recalculation, UPI-2 is invalidated and the position reverts to **UPI-1**

**Regulatory Requirement**: All trades that were submitted to the regulator with UPI-2 are now invalid and must be:
- Marked as invalid in regulatory records
- Re-submitted with the correct UPI-1

## Solution

### 1. Detection of UPI Invalidation

In `RecalculationService.recalculatePosition()`, after creating the corrected snapshot:

```java
// Check if UPI changed (UPI-2 invalidated)
String previousUPI = currentSnapshot.getUti();
String newUPI = correctedSnapshot.getUti();

if (previousUPI != null && newUPI != null && !previousUPI.equals(newUPI)) {
    // UPI-2 has been invalidated
    // Find all trades associated with the invalidated UPI-2
    List<String> invalidatedTradeIds = findTradesForUPI(positionKey, previousUPI, replayedEvents);
    
    if (!invalidatedTradeIds.isEmpty()) {
        // Publish regulatory event for UPI invalidation
        publishUPIInvalidationEvent(positionKey, previousUPI, newUPI, invalidatedTradeIds, backdatedTrade);
    }
}
```

### 2. Trade Identification

The `findTradesForUPI()` method identifies all trades associated with a specific UPI by:
1. Finding the `NEW_TRADE` event that created the UPI (tradeId == UPI)
2. Collecting all subsequent trades until a new UPI is created
3. Returning the list of trade IDs that need to be invalidated

### 3. Regulatory Event Publishing

When UPI invalidation is detected, the system publishes two types of regulatory events:

#### 3a. UPI Invalidation Event

The `publishUPIInvalidationEvent()` method creates and publishes a summary regulatory event with:

**Event Structure:**
```json
{
  "type": "UPI_INVALIDATION",
  "positionKey": "POS-XXX",
  "invalidatedUPI": "T-UPI-002-XXX",
  "newUPI": "T-UPI-001-XXX",
  "invalidatedTradeIds": ["T-UPI-002-XXX", "T-UPI-003-XXX", "T-UPI-004-XXX"],
  "reason": "BACKDATED_TRADE_RECALCULATION",
  "backdatedTradeId": "T-BACKDATED-001",
  "effectiveDate": "2025-12-09",
  "occurredAt": "2025-12-12T18:30:00Z",
  "actionRequired": "RESUBMIT_TRADES_WITH_NEW_UPI",
  "message": "Trades submitted with UPI T-UPI-002-XXX are invalid. These trades must be re-submitted with UPI T-UPI-001-XXX."
}
```

**Event Fields:**
- `type`: Always "UPI_INVALIDATION"
- `positionKey`: The position identifier
- `invalidatedUPI`: The UPI that is now invalid (UPI-2)
- `newUPI`: The correct UPI to use (UPI-1)
- `invalidatedTradeIds`: List of trade IDs that were submitted with the invalidated UPI
- `reason`: Why the UPI was invalidated (BACKDATED_TRADE_RECALCULATION)
- `backdatedTradeId`: The trade ID that caused the invalidation
- `effectiveDate`: Effective date of the backdated trade
- `occurredAt`: Timestamp when the invalidation was detected
- `actionRequired`: What action the regulator must take
- `message`: Human-readable message explaining the invalidation

#### 3b. Trade Correction Events

The `publishTradeCorrectionEvents()` method publishes individual correction events for each affected trade:

**Event Structure (per trade)**:
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

**Purpose**: Provides detailed correction instructions for each individual trade that needs to be updated.

### 4. Message Publishing

The regulatory event is published to the `regulatory-events` Kafka topic via `MessageProducer.publishRegulatoryEvent()`.

**Kafka Topic**: `regulatory-events`
**Partition Key**: `positionKey` (ensures events for the same position are ordered)

## Implementation Details

### Files Modified

1. **`domain/src/main/java/com/bank/esps/domain/messaging/MessageProducer.java`**
   - Added `publishRegulatoryEvent()` method

2. **`application/src/main/java/com/bank/esps/application/service/RecalculationService.java`**
   - Added `RegulatorySubmissionService` dependency
   - Added backdated trade submission to regulator
   - Added UPI change detection logic
   - Added `findTradesForUPI()` method
   - Added `publishUPIInvalidationEvent()` method
   - Added `publishTradeCorrectionEvents()` method

3. **`infrastructure/src/main/java/com/bank/esps/infrastructure/messaging/kafka/KafkaMessageProducer.java`**
   - Implemented `publishRegulatoryEvent()` method

4. **`infrastructure/src/main/java/com/bank/esps/infrastructure/messaging/solace/SolaceMessageProducer.java`**
   - Implemented `publishRegulatoryEvent()` method (template)

## Regulatory Workflow

1. **Backdated Trade Arrives**: A trade with an effective date before the current position state
2. **Coldpath Recalculation**: The `RecalculationService` replays all events chronologically
3. **Backdated Trade Submission**: The backdated trade is submitted to regulator (like hotpath trades)
4. **UPI Change Detected**: The corrected snapshot has a different UPI than the current snapshot
5. **Trade Identification**: All trades associated with the invalidated UPI are identified
6. **UPI Invalidation Event Published**: Summary event published to `regulatory-events` topic
7. **Trade Correction Events Published**: Individual correction event for each affected trade
8. **Regulator Notification**: The regulatory reporting system consumes the events and:
   - Marks the affected trades as invalid in regulatory records
   - Processes individual correction events for each trade
   - Triggers re-submission workflow for affected trades with the new UPI
   - Sends notifications to relevant parties

## Error Handling

- If trade identification fails, the error is logged but recalculation continues
- If regulatory event publishing fails, the error is logged but recalculation continues
- Regulatory event publishing does not block the recalculation process

## Logging

- **WARN**: When UPI change is detected: `"UPI changed from {} to {} for position {} - UPI-2 invalidated"`
- **INFO**: When trades are found: `"Found {} trades associated with UPI {} for position {}"`
- **WARN**: When regulatory event is published: `"Published UPI invalidation regulatory event for position {}: UPI {} -> {}, {} trades affected"`
- **ERROR**: If publishing fails: `"Error publishing UPI invalidation regulatory event for position {}"`

## Testing

To test the UPI invalidation regulatory event:

1. Create a position with UPI-1
2. Close the position (DECREASE to zero)
3. Create a new trade (creates UPI-2)
4. Submit a backdated trade that affects the position before UPI-1 termination
5. Trigger recalculation
6. Verify that:
   - UPI reverts to UPI-1
   - Regulatory event is published to `regulatory-events` topic
   - Event contains correct invalidated UPI, new UPI, and list of affected trade IDs

## Future Enhancements

1. **Regulatory Submission Tracking**: Store regulatory submission records with UPI to enable precise invalidation
2. **Re-submission Automation**: Automatically trigger re-submission of affected trades
3. **Audit Trail**: Maintain detailed audit log of all UPI changes and regulatory notifications
4. **Compliance Reporting**: Generate compliance reports showing all UPI invalidations and re-submissions
