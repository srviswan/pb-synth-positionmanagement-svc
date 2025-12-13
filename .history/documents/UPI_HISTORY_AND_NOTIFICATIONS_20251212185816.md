# UPI History Tracking and Downstream Notifications

## Overview

This document describes the implementation of UPI history tracking, merge detection, and downstream notifications for UPI changes.

## Features Implemented

### 1. UPI History Table

A comprehensive audit trail of all UPI changes is maintained in the `upi_history` table.

**Entity**: `UPIHistoryEntity`
**Table**: `upi_history`

**Fields**:
- `history_id`: Unique identifier
- `position_key`: Position identifier
- `upi`: Current UPI
- `previous_upi`: Previous UPI (if changed)
- `status`: Current position status
- `previous_status`: Previous status (if changed)
- `change_type`: Type of change (CREATED, TERMINATED, REOPENED, INVALIDATED, MERGED, RESTORED)
- `triggering_trade_id`: Trade that caused the change
- `backdated_trade_id`: If change was caused by backdated trade
- `occurred_at`: Timestamp of the change
- `effective_date`: Effective date of the change
- `reason`: Human-readable reason
- `merged_from_position_key`: Source position if this is a merge

**Change Types**:
- `CREATED`: UPI created with first trade
- `TERMINATED`: Position closed (quantity = 0)
- `REOPENED`: New trade on terminated position (new UPI)
- `INVALIDATED`: UPI-2 invalidated, restored to UPI-1
- `MERGED`: Positions merged due to backdated trade
- `RESTORED`: UPI restored by backdated trade

### 2. UPI History Service

The `UPIHistoryService` provides methods to:

- **Record UPI Changes**: `recordUPIChange()` - Generic method for recording any UPI change
- **Record UPI Creation**: `recordUPICreation()` - When first trade creates UPI
- **Record UPI Termination**: `recordUPITermination()` - When position closes
- **Record UPI Reopening**: `recordUPIReopening()` - When new trade reopens position
- **Record UPI Invalidation**: `recordUPIInvalidation()` - When UPI-2 is invalidated
- **Record UPI Restoration**: `recordUPIRestoration()` - When backdated trade restores UPI
- **Detect and Record Merge**: `detectAndRecordMerge()` - Detects when positions should be merged
- **Query History**: `getUPIHistory()`, `getMergeEvents()` - Query methods

### 3. Integration Points

#### Hotpath Integration

In `HotpathPositionService`:

1. **UPI Creation**: When new snapshot is created
   ```java
   upiHistoryService.recordUPICreation(positionKey, snapshot.getUti(), tradeEvent);
   ```

2. **UPI Termination**: When position closes
   ```java
   upiHistoryService.recordUPITermination(positionKey, snapshot.getUti(), tradeEvent);
   ```

3. **UPI Reopening**: When new trade reopens terminated position
   ```java
   upiHistoryService.recordUPIReopening(positionKey, newUPI, previousUPI, tradeEvent);
   ```

#### Coldpath Integration

In `RecalculationService`:

1. **UPI Invalidation**: When UPI-2 is invalidated
   ```java
   upiHistoryService.recordUPIInvalidation(positionKey, invalidatedUPI, restoredUPI, backdatedTrade, reason);
   ```

2. **UPI Restoration**: When backdated trade restores position
   ```java
   upiHistoryService.recordUPIRestoration(positionKey, restoredUPI, previousUPI, backdatedTrade, reason);
   ```

3. **Merge Detection**: After UPI change
   ```java
   boolean mergeDetected = upiHistoryService.detectAndRecordMerge(positionKey, newUPI, backdatedTrade);
   ```

### 4. UPI Merge Detection

**When Merges Occur**:
- A backdated trade affects multiple positions
- The recalculation results in positions having the same UPI
- Positions that were previously separate are now combined

**Detection Logic**:
1. After UPI change, check if other positions have the same UPI
2. If found, record merge events for each source position
3. Track `merged_from_position_key` to maintain audit trail

**Example Scenario**:
- Position A has UPI-1
- Position B has UPI-2
- Backdated trade affects both positions
- After recalculation, both positions have UPI-1
- System detects merge and records: Position A merged from Position B

### 5. Downstream Notifications

When UPI changes occur (especially due to backdated trades), downstream systems must be notified.

**Notification Event Structure**:
```json
{
  "type": "UPI_CHANGE_NOTIFICATION",
  "positionKey": "POS-XXX",
  "previousUPI": "T-UPI-002-XXX",
  "newUPI": "T-UPI-001-XXX",
  "changeReason": "BACKDATED_TRADE_RECALCULATION",
  "backdatedTradeId": "T-BACKDATED-001",
  "effectiveDate": "2025-12-09",
  "occurredAt": "2025-12-12T18:30:00Z",
  "invalidatedTradeIds": ["T-UPI-002-XXX", "T-UPI-003-XXX"],
  "mergeDetected": false,
  "actionRequired": "UPDATE_POSITION_REFERENCES",
  "message": "UPI changed from T-UPI-002-XXX to T-UPI-001-XXX due to backdated trade T-BACKDATED-001. All downstream systems must update position references.",
  "affectedSystems": ["RISK", "P_AND_L", "REPORTING", "SETTLEMENT"]
}
```

**Published To**: `historical-position-corrected-events` Kafka topic (via `publishCorrectionEvent`)

**Affected Systems**:
- **RISK**: Must update risk calculations with new UPI
- **P_AND_L**: Must update P&L attribution with new UPI
- **REPORTING**: Must update regulatory reports with new UPI
- **SETTLEMENT**: Must update settlement instructions with new UPI

## Database Schema

### Migration: V3__create_upi_history_table.sql

Creates the `upi_history` table with:
- Primary key: `history_id` (UUID)
- Foreign key: `position_key` references `snapshot_store`
- Indexes on: `position_key`, `upi`, `occurred_at`, `change_type`, `effective_date`, `merged_from_position_key`

## Usage Examples

### Query UPI History

```java
// Get all UPI changes for a position
List<UPIHistoryEntity> history = upiHistoryService.getUPIHistory("POS-XXX");

// Get all merge events
List<UPIHistoryEntity> merges = upiHistoryService.getMergeEvents();
```

### Repository Queries

```java
// Find all changes for a position
List<UPIHistoryEntity> changes = upiHistoryRepository
    .findByPositionKeyOrderByOccurredAtDesc("POS-XXX");

// Find all changes for a UPI
List<UPIHistoryEntity> upiChanges = upiHistoryRepository
    .findByUpiOrderByOccurredAtDesc("T-UPI-001-XXX");

// Find changes in date range
List<UPIHistoryEntity> dateRangeChanges = upiHistoryRepository
    .findChangesInDateRange("POS-XXX", fromDate, toDate);
```

## Benefits

1. **Complete Audit Trail**: Every UPI change is recorded with full context
2. **Compliance**: Regulatory audit requirements met with detailed history
3. **Debugging**: Easy to trace UPI changes and understand position lifecycle
4. **Merge Detection**: Automatic detection of position merges
5. **Downstream Integration**: Systems are automatically notified of UPI changes
6. **Historical Analysis**: Query UPI history for reporting and analysis

## Error Handling

- **Non-blocking**: History tracking failures don't block trade processing
- **Logging**: All errors are logged for debugging
- **Graceful Degradation**: If history service fails, processing continues

## Future Enhancements

1. **UPI History Dashboard**: Visualize UPI changes over time
2. **Merge Resolution**: Automated handling of merge conflicts
3. **Downstream Acknowledgment**: Track which systems have processed notifications
4. **Historical Queries**: Advanced queries for compliance reporting
5. **UPI Analytics**: Analyze UPI change patterns and trends
