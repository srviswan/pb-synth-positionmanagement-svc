# Event Ordering Fix for Same-Date Events

## Problem
When multiple trades have the same effective date, they were ordered by version number, which meant:
- Backdated trades (higher version numbers) were processed **AFTER** original events
- This violated chronological ordering principles

## Solution
Changed ordering to use **timestamp (`occurredAt`)** as the primary tie-breaker for same-date events.

## Changes Made

### 1. Updated Replay Ordering (RecalculationService.java, lines 215-219)

**Before:**
```java
allEventsWithBackdated.sort(Comparator
    .comparing(EventEntity::getEffectiveDate)
    .thenComparing(EventEntity::getEventVer));  // Version-based tie-breaker
```

**After:**
```java
allEventsWithBackdated.sort(Comparator
    .comparing(EventEntity::getEffectiveDate)  // Primary: effective date
    .thenComparing(EventEntity::getOccurredAt) // Secondary: timestamp (tie-breaker)
    .thenComparing(EventEntity::getEventVer));  // Final: version (deterministic fallback)
```

### 2. Updated Event Creation for Backdated Trades (RecalculationService.java, lines 223-250)

**Before:**
```java
event.setOccurredAt(java.time.OffsetDateTime.now());  // Always current time
```

**After:**
```java
// For backdated trades, set occurredAt to start of effective date (midnight)
// This ensures backdated trades are processed before same-date events
if (trade.getSequenceStatus() == TradeSequenceStatus.BACKDATED) {
    occurredAt = trade.getEffectiveDate()
            .atStartOfDay()
            .atOffset(java.time.ZoneOffset.UTC);
} else {
    occurredAt = java.time.OffsetDateTime.now();
}
```

### 3. Updated Repository Query (EventStoreRepository.java, line 49)

**Before:**
```sql
ORDER BY e.effectiveDate ASC, e.eventVer ASC
```

**After:**
```sql
ORDER BY e.effectiveDate ASC, e.occurredAt ASC, e.eventVer ASC
```

## Ordering Logic

### Three-Level Ordering

1. **Primary: `effectiveDate`** (chronological date)
   - Events are grouped by their effective date
   
2. **Secondary: `occurredAt`** (timestamp)
   - For same-date events, orders by when they actually occurred
   - Backdated trades get midnight timestamp (processed first)
   - Current trades get current timestamp (processed later)
   
3. **Tertiary: `eventVer`** (version number)
   - Final tie-breaker for deterministic ordering
   - Ensures consistent ordering even if timestamps are identical

## Example Scenarios

### Scenario 1: Multiple Trades Same Day (Hotpath)

```
09:00 - Trade A (effective: 2025-12-10) → occurredAt: 2025-12-10T09:00, version: 1
10:00 - Trade B (effective: 2025-12-10) → occurredAt: 2025-12-10T10:00, version: 2
11:00 - Trade C (effective: 2025-12-10) → occurredAt: 2025-12-10T11:00, version: 3

Replay order: version 1, version 2, version 3 ✅
(Ordered by occurredAt timestamp)
```

### Scenario 2: Backdated Trade Same Day (Coldpath)

```
Existing: Trade A (effective: 2025-12-10, occurredAt: 2025-12-10T09:00, version: 1)
Existing: Trade B (effective: 2025-12-10, occurredAt: 2025-12-10T10:00, version: 2)
Backdated: Trade C (effective: 2025-12-10, occurredAt: 2025-12-10T00:00, version: 3)

Replay order: version 3 (midnight), version 1 (09:00), version 2 (10:00) ✅
(Backdated trade processed FIRST, as it should be)
```

### Scenario 3: Backdated Trade Before Same-Day Events

```
Existing: Trade A (effective: 2025-12-10, occurredAt: 2025-12-10T09:00, version: 1)
Backdated: Trade B (effective: 2025-12-09, occurredAt: 2025-12-09T00:00, version: 2)
Existing: Trade C (effective: 2025-12-10, occurredAt: 2025-12-10T10:00, version: 3)

Replay order: version 2 (2025-12-09), version 1 (2025-12-10 09:00), version 3 (2025-12-10 10:00) ✅
```

## Benefits

### ✅ Proper Chronological Ordering
- Backdated trades (older timestamps) processed before same-date events
- Matches business expectations

### ✅ Deterministic Ordering
- Three-level ordering ensures consistent results
- Version number as final tie-breaker prevents ambiguity

### ✅ Database Consistency
- Repository queries use same ordering logic
- Ensures consistent results across all queries

## Implementation Details

### Backdated Trade Timestamp Strategy

For backdated trades, we set `occurredAt` to **midnight (00:00:00)** of the effective date:
- Ensures backdated trades are processed **before** same-date events
- Simple and predictable
- Works even if we don't know the exact original timestamp

### Current/Forward-Dated Trade Timestamp Strategy

For current/forward-dated trades, we use the **current timestamp**:
- Reflects when the trade was actually processed
- Maintains chronological order within the same day

## Testing

To verify the fix works:

1. **Create multiple trades on the same day** (hotpath)
   - Verify they're ordered by timestamp
   
2. **Create a backdated trade with same effective date**
   - Verify it's processed FIRST (midnight timestamp)
   
3. **Query events from database**
   - Verify repository query returns events in correct order

## Summary

- ✅ **Primary ordering**: `effectiveDate` (chronological date)
- ✅ **Secondary ordering**: `occurredAt` (timestamp - tie-breaker)
- ✅ **Tertiary ordering**: `eventVer` (version - deterministic fallback)
- ✅ **Backdated trades**: Get midnight timestamp (processed first)
- ✅ **Current trades**: Get current timestamp (processed in order)

The fix ensures proper chronological ordering while maintaining deterministic behavior.
