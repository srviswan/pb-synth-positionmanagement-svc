# Hotpath Regulatory Submission

## Overview

Regulatory submission is now integrated into the hotpath flow. Every trade processed in the hotpath is automatically submitted to regulatory authorities after successful processing.

## Flow

### 1. Trade Processing (Hotpath)

When a current-dated or forward-dated trade is processed:

1. **Event Saved**: Trade event is persisted to the event store
2. **Snapshot Updated**: Position snapshot is updated with new state
3. **Regulatory Submission**: Trade is automatically submitted to regulatory authorities

### 2. Regulatory Submission Process

The `RegulatorySubmissionService.submitTradeToRegulator()` method:

1. **Creates Submission Record**: Stores a `RegulatorySubmissionEntity` with status `PENDING`
2. **Creates Regulatory Event**: Builds a comprehensive regulatory event payload
3. **Publishes to Regulatory Topic**: Sends event to `regulatory-events` Kafka topic
4. **Updates Status**: Asynchronously updates submission status to `SUBMITTED` on successful publish

### 3. Regulatory Event Structure

**Event Type**: `TRADE_REPORT`

**Payload**:
```json
{
  "type": "TRADE_REPORT",
  "submissionId": "uuid",
  "tradeId": "T-001",
  "positionKey": "POS-XXX",
  "upi": "T-UPI-001",
  "tradeType": "NEW_TRADE",
  "quantity": 1000,
  "price": 50.00,
  "effectiveDate": "2025-12-12",
  "contractId": "CONTRACT-001",
  "correlationId": "CORR-001",
  "causationId": "CAUS-001",
  "userId": "USER-001",
  "positionStatus": "ACTIVE",
  "positionVersion": 1,
  "submittedAt": "2025-12-12T18:30:00Z",
  "actionRequired": "ACKNOWLEDGE_TRADE_REPORT"
}
```

**Key Fields**:
- `submissionId`: Unique identifier for the regulatory submission
- `upi`: Unique Position Identifier (from snapshot)
- `positionStatus`: Current position status (ACTIVE/TERMINATED)
- `positionVersion`: Snapshot version number
- `actionRequired`: What the regulator should do with this report

## Implementation Details

### Files Created/Modified

1. **`RegulatorySubmissionService.java`** (NEW)
   - Handles regulatory submission logic
   - Creates submission records
   - Publishes regulatory events
   - Manages submission status

2. **`HotpathPositionService.java`** (MODIFIED)
   - Added `RegulatorySubmissionService` dependency
   - Calls `submitTradeToRegulator()` after snapshot is saved
   - Regulatory submission happens after event and snapshot are persisted

### Integration Point

In `HotpathPositionService.processCurrentDatedTrade()`:

```java
// 4. Update snapshot (cache)
updateSnapshot(snapshot, state, expectedVersion, ReconciliationStatus.RECONCILED, tradeEvent);
SnapshotEntity savedSnapshot = snapshotRepository.saveAndFlush(snapshot);

// 5. Submit to regulatory authorities (hotpath)
regulatorySubmissionService.submitTradeToRegulator(tradeEvent, savedSnapshot);
```

### Error Handling

- **Non-blocking**: Regulatory submission failures do not block trade processing
- **Logging**: All errors are logged but don't cause transaction rollback
- **Status Tracking**: Submission status is tracked in `RegulatorySubmissionEntity`
- **Retry Support**: Failed submissions can be retried using `retrySubmission()`

## Regulatory Submission Status

**Status Flow**:
1. `PENDING` - Initial status when submission record is created
2. `SUBMITTED` - Status updated after successful publish to Kafka topic
3. `ACCEPTED` - Status updated when regulator acknowledges (via response handler)
4. `REJECTED` - Status updated when regulator rejects (via response handler)
5. `FAILED` - Status set if publishing fails

## Regulatory Response Handling

The `updateSubmissionStatus()` method allows updating submission status when regulatory responses are received:

```java
regulatorySubmissionService.updateSubmissionStatus(
    submissionId, 
    "ACCEPTED", 
    responsePayload, 
    null
);
```

## Benefits

1. **Compliance**: All trades are automatically submitted to regulators
2. **Audit Trail**: Complete record of all regulatory submissions
3. **Non-blocking**: Regulatory submission doesn't impact trade processing latency
4. **Traceability**: Each submission has a unique ID and correlation tracking
5. **Status Tracking**: Full lifecycle tracking of regulatory submissions

## Coldpath vs Hotpath

- **Hotpath**: Submits trades immediately after processing (current/forward-dated trades)
- **Coldpath**: Publishes UPI invalidation events when backdated trades cause UPI changes

Both paths publish to the same `regulatory-events` Kafka topic but with different event types:
- Hotpath: `TRADE_REPORT`
- Coldpath: `UPI_INVALIDATION`

## Future Enhancements

1. **Response Handler**: Consumer for regulatory responses to update submission status
2. **Retry Mechanism**: Automatic retry of failed submissions
3. **Compliance Reporting**: Dashboard showing submission status and compliance metrics
4. **Batch Submission**: Support for batch regulatory submissions
5. **Schema Registry**: Use Avro/Protobuf schemas for regulatory events
