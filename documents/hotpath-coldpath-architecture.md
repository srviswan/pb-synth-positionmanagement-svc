# Hotpath/Coldpath Architecture: Lambda/Kappa Pattern for Backdated Trades

## Executive Summary

This document describes the refined architecture that separates real-time trade processing (Hotpath) from asynchronous historical recalculation (Coldpath) to ensure backdated trades do not block the main trade flow. This follows the Lambda/Kappa Architecture pattern.

## Architecture Principles

1. **Hotpath**: Low-latency, synchronous processing for current/forward-dated trades
2. **Coldpath**: Asynchronous, non-blocking processing for backdated trades
3. **Provisional Positions**: Temporary "dirty" positions for backdated trades until recalculation completes
4. **Eventual Consistency**: Read model may temporarily show provisional positions, corrected by coldpath

## System Architecture

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Trade Capture System                          │
└────────────────────────────┬──────────────────────────────────────┘
                             │
                             ▼
                    ┌────────────────┐
                    │  Trade Events   │
                    │  (Kafka Topic)  │
                    └────────┬────────┘
                             │
                ┌────────────┴────────────┐
                │                         │
                ▼                         ▼
        ┌──────────────┐         ┌──────────────┐
        │   HOTPATH    │         │   COLDPATH   │
        │ (Real-Time)  │         │ (Async)      │
        └──────────────┘         └──────────────┘
```

## 1. The Hotpath (Real-Time Trade Processing)

### Purpose
Process all new trades for immediate risk/position impact, guaranteeing sequential ordering for the current day.

### Components

#### 1.1 Trade Processing Service (TPS)
- **Primary Consumer**: Consumes from main `trade-events` Kafka topic
- **Responsibility**: Immediate trade sequencing and validation

#### 1.2 Trade Classification Logic

```java
public enum TradeSequenceStatus {
    CURRENT_DATED,      // Effective date >= latest snapshot date
    FORWARD_DATED,     // Effective date > current date
    BACKDATED          // Effective date < latest snapshot date
}
```

**Decision Flow:**
```
Trade Arrives
    │
    ├─→ Effective Date >= Latest Snapshot Date?
    │   ├─→ YES → CURRENT_DATED or FORWARD_DATED
    │   │   └─→ Process in Hotpath (synchronous)
    │   │
    │   └─→ NO → BACKDATED
    │       └─→ Route to Coldpath (asynchronous)
```

#### 1.3 Current/Forward-Dated Trade Processing

**Synchronous Flow:**
1. **Load Snapshot**: Get latest position snapshot
2. **Apply Trade**: Calculate new position state
3. **Generate Contract**: Synchronous contract generation (regulatory requirement)
4. **USI/Reporting**: Synchronous regulatory reporting
5. **Persist Event**: Write `TradeAppliedEvent` to event store
6. **Update Snapshot**: Update read model/cache
7. **Publish**: Emit event to downstream systems (Risk, P&L)

**Latency Target**: <100ms for 99th percentile

#### 1.4 Backdated Trade Handling (Hotpath Side)

When a backdated trade is detected:

1. **Flag as Out-of-Sequence**: Mark trade with `BACKDATED` status
2. **Route to Coldpath**: Publish to `backdated-trades` Kafka topic
3. **Calculate Provisional Position**: 
   - Apply backdated trade's expected impact to current snapshot
   - Create "dirty" position estimate
   - Mark as `PROVISIONAL` in snapshot metadata
4. **Emit Provisional Event**: 
   - `ProvisionalTradeAppliedEvent` to read model
   - Includes flag: `needsReconciliation: true`
   - Includes provisional metrics for risk calculations
5. **Continue Processing**: Do NOT block hotpath

**Key Point**: The hotpath does NOT wait for historical recalculation.

### Hotpath Event Types

```java
// Normal trade applied (synchronous)
TradeAppliedEvent {
    positionKey: String
    eventVersion: Long
    tradeId: String
    effectiveDate: LocalDate
    contractId: String
    usi: String
    status: APPLIED
}

// Provisional trade (backdated, needs reconciliation)
ProvisionalTradeAppliedEvent {
    positionKey: String
    tradeId: String
    effectiveDate: LocalDate
    provisionalMetrics: Map<String, Object>
    needsReconciliation: true
    backdatedTradeId: String
}
```

## 2. The Coldpath (Historical Recalculation Service)

### Purpose
Handle all out-of-sequence events (backdated trades) asynchronously and non-disruptively.

### Components

#### 2.1 Recalculation Service
- **Consumer**: Consumes from `backdated-trades` Kafka topic
- **Scalability**: Can scale independently from hotpath
- **Resource Isolation**: Dedicated compute resources

#### 2.2 Recalculation Process

**Step-by-Step:**

1. **Receive Backdated Trade**
   - Consume from `backdated-trades` topic
   - Extract position key and effective date

2. **Load Event Stream**
   - Query event store for position's event stream
   - Load all events for the position key
   - Identify insertion point (before backdated trade's effective date)

3. **Create Temporary Event Stream**
   - Clone events up to insertion point
   - Inject backdated trade event at correct chronological position
   - Append all subsequent events

4. **Replay Events**
   - Replay entire event stream in chronological order
   - Re-apply FIFO/LIFO/HIFO tax lot logic
   - Recalculate all position metrics

5. **Generate Corrected Snapshot**
   - Create corrected position snapshot
   - Compare with provisional snapshot
   - Identify differences

6. **Override Provisional Position**
   - Write corrected snapshot to read model/cache
   - Mark as `RECONCILED` (remove provisional flag)
   - Update event store with corrected event sequence

7. **Publish Correction Event**
   - `HistoricalPositionCorrectedEvent` to downstream systems
   - Notify Risk, P&L, and other consumers
   - Include before/after metrics

### Coldpath Event Types

```java
// Historical position correction completed
HistoricalPositionCorrectedEvent {
    positionKey: String
    backdatedTradeId: String
    correctedEventVersion: Long
    previousProvisionalVersion: Long
    correctionMetrics: {
        qtyDelta: BigDecimal
        exposureDelta: BigDecimal
        lotCountDelta: Integer
    }
    correctedSnapshot: PositionSnapshot
    timestamp: Instant
}
```

### Recalculation Service Implementation

```java
@Service
public class RecalculationService {
    
    @KafkaListener(topics = "backdated-trades")
    public void processBackdatedTrade(BackdatedTradeEvent event) {
        String positionKey = event.getPositionKey();
        LocalDate effectiveDate = event.getEffectiveDate();
        
        // 1. Load event stream
        List<EventEntity> events = eventStoreRepository
            .findByPositionKeyOrderByEventVer(positionKey);
        
        // 2. Find insertion point
        int insertIndex = findInsertionPoint(events, effectiveDate);
        
        // 3. Create corrected stream
        List<EventEntity> correctedStream = new ArrayList<>();
        correctedStream.addAll(events.subList(0, insertIndex));
        correctedStream.add(convertToEventEntity(event));
        correctedStream.addAll(events.subList(insertIndex, events.size()));
        
        // 4. Replay and recalculate
        PositionState correctedState = replayEvents(correctedStream);
        
        // 5. Generate corrected snapshot
        Snapshot correctedSnapshot = createSnapshot(correctedState);
        
        // 6. Override provisional
        snapshotRepository.save(correctedSnapshot);
        
        // 7. Publish correction event
        publishCorrectionEvent(event, correctedSnapshot);
    }
    
    private PositionState replayEvents(List<EventEntity> events) {
        PositionState state = PositionState.initial();
        for (EventEntity event : events) {
            state = applyEvent(state, event);
        }
        return state;
    }
}
```

## 3. Contract Dependency Handling

### Regulatory Constraint
**No reporting without a contract** - This remains a synchronous requirement in the hotpath.

### Hotpath Contract Flow
- **Current/Forward-Dated Trades**: 
  - Contract generation is synchronous
  - USI/reporting happens immediately
  - This is the "price" of the regulatory constraint

### Coldpath Contract Flow
- **Backdated Trades Affecting Closed Positions**:
  - Contract generation bypassed (position already settled)
  - No reporting required
  - Minimal latency impact

- **Backdated Trades Affecting Open Positions**:
  - Contract generation triggered in coldpath
  - Only after recalculated position is stable
  - Asynchronous, non-blocking

### Contract Generation Logic

```java
public class ContractGenerationService {
    
    public void generateContractIfNeeded(TradeEvent trade, PositionSnapshot snapshot) {
        // Check if position needs reporting
        if (snapshot.isOpen() && snapshot.requiresReporting()) {
            // Generate contract for backdated trade
            Contract contract = contractService.generateContract(trade);
            // Update trade with contract ID
            trade.setContractId(contract.getId());
        }
        // Closed positions don't need new contracts
    }
}
```

## 4. Data Flow Diagrams

### Hotpath Flow (Current-Dated Trade)

```
┌─────────────┐
│ Trade Event │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ TPS: Classify   │
│ Trade           │
└──────┬──────────┘
       │
       ├─→ Current/Forward-Dated?
       │   │
       │   ▼
       │ ┌─────────────────┐
       │ │ Load Snapshot    │
       │ └────────┬─────────┘
       │          │
       │          ▼
       │ ┌─────────────────┐
       │ │ Apply Trade     │
       │ └────────┬─────────┘
       │          │
       │          ▼
       │ ┌─────────────────┐
       │ │ Generate        │
       │ │ Contract        │
       │ └────────┬─────────┘
       │          │
       │          ▼
       │ ┌─────────────────┐
       │ │ USI/Reporting   │
       │ └────────┬────────┘
       │          │
       │          ▼
       │ ┌─────────────────┐
       │ │ Persist Event   │
       │ └────────┬─────────┘
       │          │
       │          ▼
       │ ┌─────────────────┐
       │ │ Update Snapshot │
       │ └─────────────────┘
       │
       └─→ Backdated?
           │
           ▼
     ┌─────────────────┐
     │ Route to        │
     │ Coldpath Topic  │
     └────────┬─────────┘
              │
              ▼
     ┌─────────────────┐
     │ Provisional     │
     │ Position        │
     └─────────────────┘
```

### Coldpath Flow (Backdated Trade)

```
┌──────────────────────┐
│ Backdated Trade      │
│ (Kafka Topic)        │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Recalculation        │
│ Service              │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Load Event Stream    │
│ for Position         │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Find Insertion       │
│ Point                │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Inject Backdated     │
│ Trade into Stream    │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Replay All Events    │
│ (Chronological)      │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Recalculate Tax Lots │
│ (FIFO/LIFO/HIFO)     │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Generate Corrected   │
│ Snapshot             │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Override Provisional │
│ Snapshot             │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Publish Correction   │
│ Event                │
└──────────────────────┘
```

## 5. Kafka Topic Structure

### Topics

1. **`trade-events`** (Hotpath Input)
   - Current and forward-dated trades
   - Partitioned by position key
   - High throughput, low latency

2. **`backdated-trades`** (Coldpath Input)
   - Backdated trades routed from hotpath
   - Partitioned by position key
   - Can handle bursts without affecting hotpath

3. **`trade-applied-events`** (Hotpath Output)
   - Successfully applied trades
   - Used by downstream systems

4. **`provisional-trade-events`** (Hotpath Output)
   - Provisional positions for backdated trades
   - Temporary until coldpath correction

5. **`historical-position-corrected-events`** (Coldpath Output)
   - Corrected positions after recalculation
   - Notifies downstream systems of corrections

## 6. Snapshot Metadata

### Snapshot Status Flags

```java
public class Snapshot {
    private String positionKey;
    private Long lastVer;
    private PositionStatus status; // ACTIVE, TERMINATED
    private ReconciliationStatus reconciliationStatus; // RECONCILED, PROVISIONAL, PENDING
    private LocalDateTime lastReconciledAt;
    private String provisionalTradeId; // If PROVISIONAL
    // ... other fields
}

public enum ReconciliationStatus {
    RECONCILED,    // Final, correct position
    PROVISIONAL,   // Temporary, may be corrected
    PENDING        // Awaiting coldpath processing
}
```

### Query Considerations

When querying positions:
- **Risk Calculations**: Can use provisional positions (with caveat)
- **Regulatory Reporting**: Should wait for reconciled positions
- **Historical Analysis**: Always use reconciled positions

## 7. Performance Characteristics

### Hotpath Performance
- **Latency**: <100ms p99 for current-dated trades
- **Throughput**: 2M trades/day sustained
- **Backdated Trade Impact**: <5ms (routing only, no recalculation)

### Coldpath Performance
- **Latency**: Seconds to minutes (acceptable for async)
- **Throughput**: Can scale independently
- **Resource Isolation**: Does not affect hotpath

### Scalability
- **Hotpath**: Scale by adding partitions/consumers
- **Coldpath**: Scale independently based on backdated trade volume
- **Resource Allocation**: Can prioritize hotpath resources

## 8. Error Handling

### Hotpath Errors
- **Contract Generation Failure**: Retry with exponential backoff
- **Event Store Failure**: Retry transaction
- **Snapshot Update Failure**: Retry with version check

### Coldpath Errors
- **Recalculation Failure**: Dead letter queue, manual intervention
- **Event Stream Corruption**: Alert and manual recovery
- **Correction Application Failure**: Retry with idempotency

### Idempotency
- All event handlers must be idempotent
- Use event IDs to detect duplicates
- Coldpath corrections are idempotent (can replay)

## 9. Monitoring & Observability

### Key Metrics

**Hotpath:**
- Trade processing latency (p50, p95, p99)
- Current-dated vs backdated trade ratio
- Contract generation latency
- Snapshot update success rate

**Coldpath:**
- Backdated trade processing latency
- Recalculation queue depth
- Correction event publication rate
- Provisional to reconciled conversion time

**System:**
- Provisional position count
- Pending reconciliations
- Correction event lag

### Alerts
- Hotpath latency > threshold
- Coldpath queue depth > threshold
- Provisional positions not reconciled within SLA
- Recalculation failures

## 10. Benefits of This Architecture

1. **Low Latency**: Hotpath not blocked by historical recalculation
2. **Scalability**: Independent scaling of hotpath and coldpath
3. **Accuracy**: Backdated trades correctly processed in chronological order
4. **Resilience**: Coldpath failures don't affect hotpath
5. **Flexibility**: Can adjust coldpath processing strategy independently
6. **Observability**: Clear separation of concerns for monitoring

## 11. Trade-offs

### Advantages
- ✅ Hotpath maintains low latency
- ✅ Backdated trades don't block real-time processing
- ✅ Independent scaling and resource allocation
- ✅ Clear separation of concerns

### Considerations
- ⚠️ Temporary provisional positions (eventual consistency)
- ⚠️ Additional complexity (two processing paths)
- ⚠️ Need to handle correction events in downstream systems
- ⚠️ Monitoring complexity (two systems to monitor)

## 12. Implementation Considerations

### Phase 1: Hotpath Implementation
- Implement trade classification logic
- Route backdated trades to coldpath topic
- Create provisional position logic
- Implement contract generation (synchronous)

### Phase 2: Coldpath Implementation
- Build recalculation service
- Implement event stream replay
- Create correction event publishing
- Implement provisional position override

### Phase 3: Integration
- Connect hotpath and coldpath
- Implement monitoring and alerting
- Add idempotency checks
- Performance testing

### Phase 4: Optimization
- Optimize coldpath recalculation performance
- Tune Kafka topic configurations
- Optimize event stream queries
- Fine-tune resource allocation
