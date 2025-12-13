# Backdated Trade Event Storage Analysis

## Current Implementation

### ✅ What's Working (Event Sourcing Principles)

1. **Events are Immutable**
   - Existing events in the event store are **NEVER updated**
   - The `RecalculationService` loads events and creates in-memory copies for replay
   - Original events remain unchanged in the database

2. **Snapshot is Updated**
   - The snapshot is updated with the recalculated state (line 125: `snapshotRepository.save(correctedSnapshot)`)
   - This is correct because snapshots are mutable projections of events
   - Uses optimistic locking via `version` field

3. **In-Memory Replay**
   - Creates a replayed event stream in memory (line 90)
   - Recalculates position state chronologically (line 95)
   - Does NOT modify original events

### ❌ Missing Implementation

**Backdated Trade Event Not Saved to Event Store**

The `RecalculationService` creates a `EventEntity` for the backdated trade (line 183):
```java
EventEntity backdatedEvent = createEventFromTrade(backdatedTrade, insertionIndex + 1);
```

However, this event is:
- ✅ Created in memory
- ✅ Used for recalculation
- ❌ **NOT saved to the event store**

## Recommended Fix

The backdated trade event **should be saved** to the event store to maintain:
1. **Complete Audit Trail**: All trades should be in the event store
2. **Event Sourcing Integrity**: The event store should contain all events that affect state
3. **Replayability**: Future recalculations should include the backdated trade

### Proposed Change

Add event persistence after creating the backdated event:

```java
// In recalculatePosition() method, after line 90:
// 3. Create event stream with backdated trade injected
List<EventEntity> replayedEvents = createReplayedEventStream(allEvents, backdatedTrade, insertionIndex);

// NEW: Save backdated trade event to event store
EventEntity backdatedEvent = replayedEvents.get(insertionIndex);
eventStoreRepository.save(backdatedEvent);
log.info("Saved backdated trade event {} to event store, version {}", 
        backdatedTrade.getTradeId(), backdatedEvent.getEventVer());
```

### Important Considerations

1. **Event Versioning**: The backdated event gets a version based on its insertion point
   - If inserted at position 2, it becomes version 3
   - Subsequent events would need version adjustments (but we don't update them)

2. **Version Conflicts**: 
   - If a backdated trade is inserted at version 3, but version 3 already exists
   - We need to handle this (either skip saving or use a different versioning strategy)

3. **Event Ordering**: 
   - Events are ordered by `eventVer` in queries
   - The backdated event will appear in the correct chronological position

## Summary

- ✅ **Events are NOT updated** (correct - immutable)
- ❌ **Backdated event is NOT saved** (should be fixed)
- ✅ **Snapshot is updated** (correct - mutable projection)
