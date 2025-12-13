# Backdated Trade Event Save - Implementation Fix

## Problem
The backdated trade event was being created in memory for recalculation but **not saved to the event store**, violating event sourcing principles.

## Solution
Added code to save the backdated trade event to the event store before recalculation.

## Changes Made

### 1. Added Event Save Logic (Lines 89-103)
- **Idempotency Check**: Checks if the backdated trade event already exists (by tradeId)
- **Save Event**: If not exists, saves the backdated trade event with the next available version number
- **Reload Events**: Reloads all events from the database to include the newly saved event

### 2. Added Helper Methods

#### `checkIfBackdatedEventExists()` (Lines 390-404)
- Checks if a backdated trade event already exists in the event store
- Uses tradeId from the event payload for comparison
- Ensures idempotency (prevents duplicate saves)

#### `saveBackdatedTradeEvent()` (Lines 406-432)
- Gets the next available version number (max existing version + 1)
- Creates EventEntity from TradeEvent
- Saves to event store using `eventStoreRepository.save()`
- Returns the saved event

### 3. Updated `createReplayedEventStream()` (Lines 183-218)
- Now finds the saved backdated event from the loaded events list
- Sorts all events by effective date (chronological order) for correct replay
- No longer creates in-memory events (uses saved events from database)

## Key Features

### ✅ Event Sourcing Compliance
- **Events are immutable**: Existing events are never updated
- **Append-only**: Backdated event is saved as a new event
- **Complete audit trail**: All trades are now in the event store

### ✅ Idempotency
- Checks if event already exists before saving
- Prevents duplicate events if recalculation runs multiple times
- Uses tradeId for uniqueness check

### ✅ Version Management
- Uses next available version number (max + 1)
- Avoids primary key conflicts
- Events ordered by effective date for chronological replay

### ✅ Chronological Ordering
- Events sorted by `effectiveDate` for replay
- Ensures correct tax lot calculation order
- Version number is for uniqueness, not ordering

## Flow Diagram

```
1. Backdated trade arrives
   ↓
2. Check if event already exists (idempotency)
   ↓
3. If not exists:
   - Get next available version (max + 1)
   - Create EventEntity
   - Save to event store
   - Reload all events
   ↓
4. Create replayed event stream (sorted by effective date)
   ↓
5. Replay events chronologically
   ↓
6. Update snapshot with corrected state
```

## Testing

To verify the fix works:

1. **Create a current trade** (establishes position)
2. **Create a backdated trade** (5 days ago)
3. **Query event store** - should see the backdated event saved
4. **Verify idempotency** - run recalculation twice, should not create duplicate

## Example

```java
// Before: Backdated event created in memory, not saved
EventEntity backdatedEvent = createEventFromTrade(backdatedTrade, insertionIndex + 1);
// ❌ Not saved to database

// After: Backdated event saved to event store
if (!checkIfBackdatedEventExists(positionKey, backdatedTrade.getTradeId())) {
    EventEntity backdatedEvent = saveBackdatedTradeEvent(allEvents, backdatedTrade, insertionIndex);
    // ✅ Saved to database with version number
}
```

## Notes

- **Version Numbers**: The backdated event gets the next available version (e.g., if max is 3, it becomes 4)
- **Ordering**: Events are sorted by `effectiveDate` for replay, not by version number
- **Primary Key**: Uses composite key (positionKey, eventVer) - no conflicts
- **Idempotency**: Prevents duplicate saves if the same backdated trade is processed multiple times
