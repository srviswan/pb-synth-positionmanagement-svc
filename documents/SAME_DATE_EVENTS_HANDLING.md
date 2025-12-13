# Handling Multiple Trades with Same Effective Date

## Current Behavior

### 1. **Event Ordering for Replay**

When events have the same effective date, they are ordered using a **tie-breaker**:

```java
// In RecalculationService.createReplayedEventStream()
allEventsWithBackdated.sort(Comparator
    .comparing(EventEntity::getEffectiveDate)  // Primary: effective date
    .thenComparing(EventEntity::getEventVer));  // Secondary: version number
```

**Ordering Logic:**
- **Primary**: `effectiveDate` (chronological order)
- **Secondary**: `eventVer` (version number - tie-breaker)

### 2. **Backdated Trade Insertion**

When a backdated trade has the same effective date as existing events:

```java
// In RecalculationService.findInsertionPoint()
if (event.getEffectiveDate().isAfter(backdatedDate) || 
    event.getEffectiveDate().isEqual(backdatedDate)) {
    return i;  // Insert BEFORE this event
}
```

**Behavior:**
- Backdated trade with same effective date is inserted **BEFORE** all existing events with that date
- Gets the next available version number (max + 1)
- When sorted for replay, it will appear **AFTER** existing events with the same date (because version number is higher)

### 3. **Potential Issue**

There's an **inconsistency** between insertion logic and replay ordering:

**Scenario:**
- Existing events on 2025-12-10: version 1, version 2
- Backdated trade arrives with effective date 2025-12-10
- Insertion point: position 0 (before existing events)
- Saved version: 3 (max + 1)
- **Replay order**: version 1, version 2, version 3 ✅ (correct chronological order by version)

**However, if the backdated trade should be processed FIRST on that date:**
- Current behavior: It will be processed LAST (because version 3 > version 2 > version 1)
- This may not match business requirements

## Repository Queries

### Event Store Queries

```sql
-- Orders by effective date, then version
ORDER BY e.effectiveDate ASC, e.eventVer ASC
```

This ensures consistent ordering in database queries.

## Business Implications

### ✅ **Current Behavior (Version-based tie-breaker)**

**Pros:**
- Deterministic ordering (always same order)
- Preserves insertion order for events processed on the same day
- Simple to implement

**Cons:**
- Backdated trades processed on the same day will appear AFTER original events
- May not match business intent if backdated trade should be processed first

### 🔄 **Alternative: Timestamp-based tie-breaker**

If business requires backdated trades to be processed **before** same-date events:

```java
// Use occurredAt (timestamp) as tie-breaker instead
allEventsWithBackdated.sort(Comparator
    .comparing(EventEntity::getEffectiveDate)
    .thenComparing(EventEntity::getOccurredAt));  // Use timestamp instead
```

**Pros:**
- Backdated trades (older timestamp) processed before same-date events
- More intuitive chronological ordering

**Cons:**
- Requires all events to have accurate timestamps
- May not match version-based ordering

## Recommendations

### Option 1: Keep Current Behavior (Recommended for now)
- **Use version number as tie-breaker**
- Document that same-date events are ordered by version
- Ensure business logic doesn't depend on specific same-date ordering

### Option 2: Use Timestamp-based Ordering
- **Use `occurredAt` as tie-breaker** for same-date events
- Ensures backdated trades (older timestamps) are processed first
- Requires verification that all events have accurate timestamps

### Option 3: Add Explicit Sequence Number
- Add a `sequenceNumber` field to EventEntity
- Assign sequence based on business rules (e.g., backdated trades get lower sequence)
- Use as tie-breaker: `effectiveDate` → `sequenceNumber` → `eventVer`

## Example Scenarios

### Scenario 1: Multiple Trades Same Day (Hotpath)

```
Time 09:00 - Trade A (effective: 2025-12-10) → version 1
Time 10:00 - Trade B (effective: 2025-12-10) → version 2
Time 11:00 - Trade C (effective: 2025-12-10) → version 3

Replay order: version 1, version 2, version 3 ✅
```

### Scenario 2: Backdated Trade Same Day (Coldpath)

```
Existing: Trade A (effective: 2025-12-10, version: 1)
Existing: Trade B (effective: 2025-12-10, version: 2)
Backdated: Trade C (effective: 2025-12-10, occurred: 08:00) → version 3

Current replay order: version 1, version 2, version 3
- Trade C processed LAST (even though it occurred first)
```

### Scenario 3: Backdated Trade Before Same-Day Events

```
Existing: Trade A (effective: 2025-12-10, version: 1)
Backdated: Trade B (effective: 2025-12-09) → version 2
Existing: Trade C (effective: 2025-12-10, version: 3)

Replay order: version 2 (2025-12-09), version 1 (2025-12-10), version 3 (2025-12-10) ✅
```

## Code Locations

1. **Replay Ordering**: `RecalculationService.createReplayedEventStream()` (lines 216-218)
2. **Insertion Point**: `RecalculationService.findInsertionPoint()` (lines 172-181)
3. **Repository Query**: `EventStoreRepository.findByPositionKeyAndEffectiveDateRange()` (line 49)

## Summary

- ✅ Events with same effective date are ordered by **version number** (tie-breaker)
- ⚠️ Backdated trades with same effective date will be processed **AFTER** original events
- 💡 Consider using `occurredAt` timestamp as tie-breaker if business requires backdated trades to be processed first
