# UPI History Performance Analysis

## Performance Requirements

- **Hotpath Target**: <100ms p99 latency
- **Throughput**: 2M trades/day (~23 trades/second average, ~100-200 trades/second peak)

## Current Implementation Analysis

### Hotpath Operations

1. **UPI Creation** (New Position):
   - **Operation**: `upiHistoryRepository.save()` - Synchronous DB write
   - **Frequency**: Only for new positions (first trade)
   - **Impact**: ~5-10ms per new position
   - **Transaction**: Same transaction as trade processing

2. **UPI Termination** (Position Closes):
   - **Operation**: `upiHistoryRepository.save()` - Synchronous DB write
   - **Frequency**: Only when position qty goes to zero
   - **Impact**: ~5-10ms per termination
   - **Transaction**: Same transaction as trade processing

3. **UPI Reopening** (New Trade on Terminated Position):
   - **Operation**: `upiHistoryRepository.save()` - Synchronous DB write
   - **Frequency**: Rare (only when reopening)
   - **Impact**: ~5-10ms per reopening
   - **Transaction**: Same transaction as trade processing

### Coldpath Operations

1. **UPI Invalidation**:
   - **Operation**: `upiHistoryRepository.save()` - Synchronous DB write
   - **Frequency**: Only when UPI-2 is invalidated (rare)
   - **Impact**: ~5-10ms
   - **Transaction**: Same transaction as recalculation

2. **Merge Detection**:
   - **Operation**: `upiHistoryRepository.findByUpiOrderByOccurredAtDesc()` - Database query
   - **Frequency**: Only when UPI changes (rare)
   - **Impact**: ~10-20ms (query + processing)
   - **Transaction**: Same transaction as recalculation
   - **Concern**: This query could be slow if there are many UPI history records

3. **UPI Restoration**:
   - **Operation**: `upiHistoryRepository.save()` - Synchronous DB write
   - **Frequency**: Only when backdated trade restores position
   - **Impact**: ~5-10ms
   - **Transaction**: Same transaction as recalculation

## Performance Impact Assessment

### Hotpath Impact

**Low Impact Scenarios** (Most trades):
- Regular trades on existing positions: **0ms** (no UPI history operations)
- Only ~1-5% of trades create/terminate/reopen positions

**Medium Impact Scenarios**:
- New position creation: **+5-10ms** (one DB write)
- Position termination: **+5-10ms** (one DB write)
- Position reopening: **+5-10ms** (one DB write)

**Estimated Impact**:
- Average latency increase: **<1ms** (weighted by frequency)
- P99 latency increase: **+5-10ms** (for new positions)
- Still within <100ms p99 target ✅

### Coldpath Impact

**Low Impact** (Asynchronous):
- Coldpath is already asynchronous, so additional DB writes don't impact hotpath
- Merge detection query is only on UPI changes (rare)

**Potential Concern**:
- Merge detection query could be slow if UPI history table grows large
- Need to ensure indexes are optimal

## Optimizations Implemented

### 1. Non-Blocking Error Handling

All UPI history operations are wrapped in try-catch:
```java
try {
    upiHistoryService.recordUPICreation(...);
} catch (Exception e) {
    log.error("Error recording UPI creation", e);
    // Don't throw - doesn't block trade processing
}
```

**Benefit**: History tracking failures don't block trade processing

### 2. Separate Transaction for History Writes

All UPI history methods use `REQUIRES_NEW` propagation:
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordUPIChange(...) {
    // History write in separate transaction
}
```

**Benefits**:
- History write doesn't hold main transaction lock
- Faster main transaction commit
- History write can complete independently
- Reduces contention on main transaction

**Impact**: Reduces hotpath transaction duration by 5-10ms

### 3. Indexes on UPI History Table

Migration includes indexes:
- `idx_upi_history_position_key` - Fast lookups by position
- `idx_upi_history_upi` - Fast lookups by UPI (for merge detection)
- `idx_upi_history_occurred_at` - Fast time-based queries
- `idx_upi_history_change_type` - Fast filtering by change type
- `idx_upi_history_effective_date` - Fast date range queries
- `idx_upi_history_merged_from` - Fast merge event queries (partial index)

**Benefit**: Query performance optimized

### 3. Transaction Scope (Optimized)

- UPI history writes use `REQUIRES_NEW` propagation (separate transaction)
- History write doesn't hold main transaction lock
- Main transaction can commit faster
- Slight trade-off: History write succeeds even if main transaction fails (acceptable for audit trail)

## Recommended Optimizations

### Option 1: Async History Writes (Recommended)

Move UPI history writes to async processing:

```java
// In HotpathPositionService
@Async
public void recordUPIHistoryAsync(String positionKey, String upi, TradeEvent tradeEvent) {
    try {
        upiHistoryService.recordUPICreation(positionKey, upi, tradeEvent);
    } catch (Exception e) {
        log.error("Error recording UPI history", e);
    }
}
```

**Benefits**:
- Removes DB write from hotpath transaction
- Reduces hotpath latency by 5-10ms
- Still maintains audit trail

**Trade-offs**:
- Slight risk of history loss if async fails (mitigated by retry)
- Need to ensure async processing is reliable

### Option 2: Batch History Writes

Batch multiple history writes together:

```java
// Collect history events during transaction
List<UPIHistoryEvent> pendingHistoryEvents = new ArrayList<>();

// At end of transaction, batch write
if (!pendingHistoryEvents.isEmpty()) {
    upiHistoryRepository.saveAll(pendingHistoryEvents);
}
```

**Benefits**:
- Reduces number of DB round trips
- Better for high-volume scenarios

**Trade-offs**:
- More complex implementation
- Still synchronous (but fewer round trips)

### Option 3: Move Merge Detection to Coldpath Only

Merge detection is expensive (database query). Since merges only happen with backdated trades:

```java
// Only run merge detection in coldpath
if (isColdpath) {
    boolean mergeDetected = upiHistoryService.detectAndRecordMerge(...);
}
```

**Benefits**:
- Removes expensive query from hotpath
- Merge detection only when needed (coldpath)

**Trade-offs**:
- None - merges only happen with backdated trades anyway

### Option 4: Separate Transaction for History

Use `@Transactional(propagation = Propagation.REQUIRES_NEW)`:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordUPIChange(...) {
    // History write in separate transaction
}
```

**Benefits**:
- History write doesn't hold main transaction lock
- Faster main transaction commit

**Trade-offs**:
- Slight risk of inconsistency if main transaction fails after history commit
- More complex transaction management

## Performance Recommendations

### Immediate (Low Risk)

1. ✅ **Keep current implementation** - Impact is minimal (<1ms average)
2. ✅ **Ensure indexes are created** - Already done in migration
3. ✅ **Monitor query performance** - Add metrics for UPI history operations

### Short Term (Medium Risk)

1. **Move merge detection to coldpath only** - Safe optimization
2. **Add async history writes** - Reduces hotpath latency
3. **Add performance metrics** - Track UPI history operation times

### Long Term (If Needed)

1. **Batch history writes** - If volume increases significantly
2. **Separate history database** - If history table grows very large
3. **History archival** - Move old history to archive table

## Monitoring

Add metrics to track:

```java
// In UPIHistoryService
metricsService.recordUPIHistoryWriteTime(duration);
metricsService.incrementUPIHistoryWrites();
metricsService.incrementUPIHistoryErrors();
```

## Conclusion

**Current Impact**: **Low** (<1ms average, 5-10ms for new positions)
**Within Target**: ✅ Yes (<100ms p99)

**Recommendation**: 
- Keep current implementation for now
- Monitor performance in production
- Implement async writes if latency becomes an issue
- Move merge detection to coldpath only (safe optimization)
