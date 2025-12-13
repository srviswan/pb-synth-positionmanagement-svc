# Position Management Service - Implementation Plan

## Executive Summary

This document outlines the implementation plan for an Event-Sourced Position Service (ESPS) designed to handle high-throughput trade processing (2M trades/day) with tax lot management, optimistic locking, and partitioned event storage.

## System Overview

The system implements a **Hotpath/Coldpath architecture** (Lambda/Kappa pattern) that separates real-time trade processing from asynchronous historical recalculation. The system processes trade events from Kafka, maintains position state with tax lot tracking, and persists events in a partitioned PostgreSQL event store with Redis/Postgres snapshots for fast reads.

### Architecture Pattern
- **Hotpath**: Low-latency (<100ms p99) synchronous processing for current/forward-dated trades
- **Coldpath**: Asynchronous processing for backdated trades with full event stream replay
- **Provisional Positions**: Temporary positions for backdated trades until coldpath reconciliation
- **Eventual Consistency**: Read model may temporarily show provisional positions

### Key Architecture Components

1. **Event Sourcing**: Append-only event store with version-based optimistic locking
2. **Snapshot Pattern**: Hot cache in Redis/Postgres for fast state reconstruction
3. **Partitioned Storage**: Hash-partitioned event store (16 partitions) for scalability
4. **Compressed Storage**: Parallel arrays for tax lots to reduce JSON size by 40-60%
5. **Hotpath/Coldpath Separation**: Lambda/Kappa architecture pattern separating real-time processing from asynchronous historical recalculation

## Implementation Phases

### Phase 1: Foundation & Project Setup (Week 1)

#### 1.1 Project Structure
- Initialize Spring Boot 3.x project
- Set up Maven/Gradle build configuration
- Configure multi-module structure (if needed):
  - `domain`: Core business logic and models
  - `infrastructure`: Database, Kafka, external integrations
  - `application`: Service layer and orchestration
  - `api`: REST endpoints (if needed)

#### 1.2 Dependencies
```xml
- Spring Boot Starter Web
- Spring Data JPA
- Spring Kafka
- PostgreSQL Driver
- Redis (Lettuce or Jedis)
- Jackson for JSON processing
- Lombok (optional, for reducing boilerplate)
- Flyway or Liquibase for migrations
- Resilience4j (Circuit breakers, retries, rate limiting, timeouts)
- Micrometer (Metrics for Prometheus)
- OpenTelemetry (Distributed tracing)
- Confluent Schema Registry Client (Avro/Protobuf schema management)
- Avro or Protobuf (Schema serialization)
- JUnit 5, Mockito for testing
- Testcontainers (for integration testing)
```

#### 1.3 Configuration
- Application properties for:
  - Database connections (Postgres + Redis)
  - Kafka broker configuration
  - Partitioning strategy
  - Retry policies
  - Logging configuration
  - Circuit breaker configuration
  - Timeout configuration
  - Rate limiting configuration
  - Backpressure thresholds
  - Health check configuration
  - Schema Registry configuration
  - Validation rules configuration
  - Reconciliation configuration

### Phase 2: Database Layer (Week 1-2)

#### 2.1 Event Store Schema
- [ ] Create Flyway/Liquibase migration scripts
- [ ] Implement partitioned table structure:
  - Main `event_store` table with hash partitioning
  - Create all 16 partitions (p0 through p15)
  - Add indexes: `idx_event_replay`, `idx_contract_link`
  - Constraints: Primary key on (position_key, event_ver)

#### 2.2 Snapshot Store Schema
- [ ] Create `snapshot_store` table
- [ ] Index on `position_key` (primary key)
- [ ] Index on `last_updated_at` for cleanup jobs

#### 2.3 Database Extensions
- [ ] Enable `uuid-ossp` extension
- [ ] Configure connection pooling (HikariCP)
- [ ] Set up read replicas if needed

#### 2.4 Repository Interfaces
- [ ] `EventStoreRepository`: 
  - `save(EventEntity)` with version conflict detection
  - `findByPositionKeyAndVersionRange()` for replay
  - `findLatestVersion(positionKey)`
  - `findByPositionKeyOrderByEventVer()` for coldpath replay
  - `findByPositionKeyAndEffectiveDateRange()` for insertion point finding
- [ ] `SnapshotRepository`:
  - `findById(positionKey)` with optimistic locking
  - `save(Snapshot)` with version check
  - `findAllByLastUpdatedBefore()` for cleanup
  - `findAllByReconciliationStatus()` for monitoring provisional positions
  - `updateReconciliationStatus()` for provisional to reconciled transitions

### Phase 3: Domain Models (Week 2)

#### 3.1 Core Entities
- [ ] `EventEntity`: 
  - Fields: position_key, event_ver, event_type, effective_date, occurred_at, payload (JSONB), meta_lots, correlation_id, contract_id
  - JPA annotations for partitioning
  - Support for both hotpath and coldpath events
- [ ] `Snapshot`:
  - Fields: position_key, last_ver, uti, status, reconciliation_status, tax_lots_compressed (JSONB), summary_metrics, last_updated_at, provisional_trade_id
  - Version field for optimistic locking
  - Reconciliation status: RECONCILED, PROVISIONAL, PENDING
  - Support for provisional position tracking

#### 3.2 Domain Objects
- [ ] `TaxLot`: 
  - id, tradeDate, currentRefPrice, remainingQty
  - Methods: `reduceQty()`, `updatePrice()`
- [ ] `CompressedLots`:
  - Parallel arrays: ids, dates, prices, qtys
  - Methods: `inflate()` → List<TaxLot>, `compress(List<TaxLot>)` → CompressedLots
- [ ] `PositionState`:
  - Aggregates tax lots
  - Methods: `getOpenLots()`, `getTotalQty()`, `getExposure()`
  - Support for provisional state tracking
- [ ] `LotAllocationResult`:
  - Tracks which lots were allocated/reduced
  - Used for audit trail in event metadata
- [ ] `TradeSequenceStatus`:
  - Enum: CURRENT_DATED, FORWARD_DATED, BACKDATED
  - Classification logic based on effective date
- [ ] `ReconciliationStatus`:
  - Enum: RECONCILED, PROVISIONAL, PENDING
  - Tracks snapshot reconciliation state

#### 3.3 Event Types
- [ ] `TradeEvent` (CDM-inspired):
  - Trade type (NEW_TRADE, INCREASE, DECREASE)
  - Quantity, price, effective date
  - Contract ID, correlation ID
  - Sequence status (CURRENT_DATED, FORWARD_DATED, BACKDATED)
- [ ] `MarketDataEvent`:
  - Price updates for reset operations
- [ ] `ContractEvent`:
  - Tax lot method (FIFO/LIFO), rules
- [ ] `ProvisionalTradeAppliedEvent`:
  - Provisional position metrics
  - needsReconciliation flag
  - backdatedTradeId reference
- [ ] `HistoricalPositionCorrectedEvent`:
  - Corrected position snapshot
  - Correction metrics (deltas)
  - Previous provisional version
  - Before/after comparison

### Phase 4: Tax Lot Engine (Week 2-3)

#### 4.1 Core Logic
- [ ] `LotLogic` interface/class:
  - `addLot(PositionState, TradeEvent)` → LotAllocationResult
  - `reduceLots(PositionState, TradeEvent, ContractRules)` → LotAllocationResult
  - Support FIFO and LIFO methods
  - Handle partial lot reductions

#### 4.2 Contract Rules Integration
- [ ] `ContractRules` model:
  - Tax lot method (FIFO/LIFO)
  - Business rules (wash sale, etc.)
- [ ] Integration with Contract Service events
- [ ] Cache contract rules in memory/Redis

#### 4.3 Edge Cases
- [ ] Handle zero quantity trades
- [ ] Handle backdated trades (replay scenario - now handled in coldpath)
- [ ] Handle price resets (market data updates)
- [ ] Validation: prevent negative quantities
- [ ] Handle provisional position calculations (hotpath)
- [ ] Handle event stream replay with injected backdated trades (coldpath)

### Phase 5: Kafka Integration (Week 3)

#### 5.1 Consumer Configuration
- [ ] Configure Kafka consumer groups
- [ ] Set up partition assignment strategy
- [ ] Configure consumer properties:
  - `enable.auto.commit=false` (manual commit)
  - `max.poll.records` tuning
  - Error handling and dead letter topics

#### 5.2 Event Consumers
- [ ] `TradeEventConsumer` (Hotpath):
  - Consume from `trade-events` topic
  - Deserialize CDM trade events
  - Route to TradeClassifier
- [ ] `BackdatedTradeConsumer` (Coldpath):
  - Consume from `backdated-trades` topic
  - Deserialize backdated trade events
  - Route to RecalculationService
- [ ] `MarketDataConsumer`:
  - Consume price/rate updates
  - Route to Reset Manager (hotpath)
- [ ] `ContractEventConsumer`:
  - Consume contract updates
  - Update contract rules cache

#### 5.3 Kafka Topics Setup
- [ ] `trade-events` (Hotpath Input):
  - Partition count: 16+ (for parallelism)
  - Replication factor: 3
  - Retention: 7 days (configurable)
  - Partition key: position_key (for ordering)
- [ ] `backdated-trades` (Coldpath Input):
  - Partition count: 8-16 (can differ from hotpath)
  - Replication factor: 3
  - Retention: 30 days (longer for replay scenarios)
  - Partition key: position_key
- [ ] `trade-applied-events` (Hotpath Output):
  - For downstream consumers (Risk, P&L)
  - Partition count: 16+
  - Replication factor: 3
- [ ] `provisional-trade-events` (Hotpath Output):
  - Provisional position notifications
  - Partition count: 16+
  - Replication factor: 3
- [ ] `historical-position-corrected-events` (Coldpath Output):
  - Correction notifications for downstream systems
  - Partition count: 16+
  - Replication factor: 3

#### 5.4 Schema Registry Setup
- [ ] Deploy Confluent Schema Registry (or use managed service)
- [ ] Configure schema registry URL and authentication
- [ ] Register initial schemas:
  - `TradeEvent` schema (Avro or Protobuf)
  - `PositionEvent` schema
  - `TradeAppliedEvent` schema
  - `ProvisionalTradeAppliedEvent` schema
  - `HistoricalPositionCorrectedEvent` schema
- [ ] Configure schema evolution strategy:
  - Backward compatibility (new consumers can read old events)
  - Forward compatibility (old consumers can read new events)
  - Full compatibility (both directions)
- [ ] Schema versioning:
  - Major version for breaking changes
  - Minor version for additive changes
  - Patch version for bug fixes
- [ ] Schema validation:
  - Validate on producer side
  - Validate on consumer side
  - Reject incompatible schemas

#### 5.5 Data Validation Gate
- [ ] **Validation Service Implementation:**
  - Early-stage validation before Kafka write
  - Business rules validation
  - Data integrity constraints
  - Schema validation (via Schema Registry)
- [ ] **Validation Rules:**
  - Trade ID uniqueness check
  - Required fields validation
  - Data type validation
  - Business date validation (not future beyond threshold)
  - Quantity validation (positive, non-zero)
  - Price validation (positive, within reasonable bounds)
  - Position key format validation
  - Contract ID validation (if provided)
- [ ] **Validation Pipeline:**
  - Ingest trade → Validate → Route
  - Pass: Route to main `trade-events` topic
  - Fail: Route to Dead Letter Queue (DLQ)
- [ ] **Dead Letter Queue (DLQ):**
  - `trade-events-dlq` topic
  - Store failed trades with error details
  - Manual review and correction workflow
  - Retry mechanism after correction
- [ ] **Error Handling Queue:**
  - `trade-events-errors` topic
  - For recoverable errors (retryable)
  - Automatic retry with exponential backoff
  - Move to DLQ after max retries
- [ ] **Validation Metrics:**
  - Validation success/failure rates
  - DLQ message count
  - Error queue depth
  - Validation latency

#### 5.6 Idempotency Implementation
- [ ] **Idempotency Key Strategy:**
  - Use Trade ID or USI as unique identifier
  - Store idempotency keys in database
  - Check before processing any trade
- [ ] **Idempotency Storage:**
  - `idempotency_store` table:
    - `idempotency_key` (PRIMARY KEY)
    - `trade_id`
    - `position_key`
    - `event_version`
    - `processed_at`
    - `status` (PROCESSED, FAILED)
- [ ] **Idempotency Check:**
  - Before processing: Check if trade_id already processed
  - If processed: Return existing result (no duplicate processing)
  - If not processed: Process and store idempotency key
- [ ] **Idempotency in Hotpath:**
  - Check at trade ingestion
  - Use database unique constraint on trade_id
  - Handle duplicate key exceptions gracefully
- [ ] **Idempotency in Coldpath:**
  - Check before recalculation
  - Ensure backdated trade not processed twice
  - Handle idempotency in event replay
- [ ] **Idempotency Cleanup:**
  - Retain idempotency keys for audit period (e.g., 90 days)
  - Archive old idempotency records
  - Periodic cleanup job

#### 5.7 Consumer Error Handling
- [ ] Retry logic with exponential backoff
- [ ] Dead letter queue for poison messages
- [ ] Monitoring and alerting
- [ ] Separate error handling strategies for hotpath vs coldpath
- [ ] Idempotency checks in error recovery

### Phase 6: Hotpath Implementation (Week 3-4)

#### 6.1 Trade Classification Service
- [ ] `TradeClassifier`:
  - Compare trade effective date with latest snapshot date
  - Classify as: CURRENT_DATED, FORWARD_DATED, or BACKDATED
  - Route based on classification

#### 6.2 Hotpath Ingestion Handler
- [ ] Process CURRENT_DATED and FORWARD_DATED trades
- [ ] Synchronous contract generation integration
- [ ] USI/Regulatory reporting (synchronous)
- [ ] Integrate with PositionService for hotpath
- [ ] Handle transaction boundaries
- [ ] Target latency: <100ms p99

#### 6.3 Backdated Trade Router
- [ ] Detect backdated trades (effective date < snapshot date)
- [ ] Publish to `backdated-trades` Kafka topic
- [ ] Calculate provisional position (dirty estimate)
- [ ] Create `ProvisionalTradeAppliedEvent`
- [ ] Update snapshot with PROVISIONAL status
- [ ] Do NOT block hotpath processing

#### 6.4 Provisional Position Manager
- [ ] Create provisional snapshots
- [ ] Mark snapshots with `needsReconciliation: true`
- [ ] Store provisional metrics for risk calculations
- [ ] Handle provisional position queries

#### 6.5 Contract Generation Service (Synchronous)
- [ ] Generate contracts for current/forward-dated trades
- [ ] Create USI for regulatory reporting
- [ ] Integrate with Contract Service
- [ ] Handle contract generation failures (retry logic)
- [ ] Circuit breaker for contract service
- [ ] Timeout: 40ms (fail fast)
- [ ] Fallback: Queue for async processing if circuit open

#### 6.6 Reset Manager (Market Data)
- [ ] Process market data price updates
- [ ] Update all open tax lots with new reference price
- [ ] Recalculate exposure/metrics
- [ ] Generate RESET events

### Phase 7: Coldpath Implementation (Week 4-5)

#### 7.1 Recalculation Service
- [ ] `RecalculationService`:
  - Consume from `backdated-trades` Kafka topic
  - Scale independently from hotpath
  - Handle backdated trade processing
  - Circuit breaker for event store queries
  - Retry logic for transient failures
  - Timeout: 5 minutes (configurable)
  - Dead letter queue for persistent failures

#### 7.2 Event Stream Loader
- [ ] Load complete event stream for a position
- [ ] Query event store by position key
- [ ] Order events by version/effective date
- [ ] Identify insertion point for backdated trade

#### 7.3 Event Replay Engine
- [ ] Create temporary event stream with backdated trade injected
- [ ] Insert backdated trade at correct chronological position
- [ ] Replay all subsequent events in order
- [ ] Maintain event version sequence

#### 7.4 Tax Lot Recalculation
- [ ] Re-apply FIFO/LIFO/HIFO logic during replay
- [ ] Recalculate all tax lot allocations
- [ ] Recompute position metrics (qty, exposure, etc.)
- [ ] Compare with provisional position

#### 7.5 Correction Generator
- [ ] Generate corrected position snapshot
- [ ] Calculate deltas (qty, exposure, lot count)
- [ ] Create `HistoricalPositionCorrectedEvent`
- [ ] Override provisional snapshot with corrected version
- [ ] Mark snapshot as RECONCILED

#### 7.6 Correction Event Publisher
- [ ] Publish correction events to downstream systems
- [ ] Notify Risk Engine of corrections
- [ ] Notify P&L systems
- [ ] Include before/after metrics

#### 7.7 Contract Generation (Coldpath)
- [ ] Generate contracts for backdated trades affecting open positions
- [ ] Only after recalculated position is stable
- [ ] Asynchronous, non-blocking
- [ ] Skip for closed positions

### Phase 8: Resiliency & Backpressure Implementation (Week 5)

#### 8.1 Circuit Breaker Pattern
- [ ] Implement circuit breakers for external dependencies:
  - Contract Generation Service (hotpath critical)
  - Database connections (both hotpath and coldpath)
  - Kafka producers (both paths)
- [ ] Use Resilience4j or Hystrix:
  - Failure threshold: 50% failures over 10 requests
  - Half-open state: Test after 60 seconds
  - Success threshold: 5 successful calls to close
- [ ] Circuit breaker states:
  - CLOSED: Normal operation
  - OPEN: Failing, reject requests immediately
  - HALF_OPEN: Testing if service recovered
- [ ] Fallback strategies:
  - Contract generation: Queue for retry, use cached contract if available
  - Database: Return cached snapshot, log for manual intervention
  - Kafka: Buffer locally, retry when circuit closes

#### 8.2 Retry Strategies
- [ ] **Hotpath Retries:**
  - Optimistic locking conflicts: Exponential backoff (50ms, 100ms, 200ms)
  - Contract generation: 3 retries with exponential backoff
  - Database timeouts: 2 retries with jitter
  - Max retry time: 200ms (to maintain <100ms p99 latency)
- [ ] **Coldpath Retries:**
  - Event stream loading: 5 retries with exponential backoff
  - Recalculation failures: Dead letter queue after 3 retries
  - Correction publishing: Infinite retries (eventual consistency)
- [ ] Retry configuration:
  - Exponential backoff: baseDelay * 2^attempt
  - Jitter: ±20% random variation
  - Max attempts: Configurable per operation type

#### 8.3 Timeout Management
- [ ] **Hotpath Timeouts:**
  - Database queries: 50ms timeout
  - Contract generation: 40ms timeout
  - Snapshot updates: 30ms timeout
  - Total hotpath timeout: 100ms (fail fast)
- [ ] **Coldpath Timeouts:**
  - Event stream loading: 5 seconds
  - Recalculation: 5 minutes (configurable per position size)
  - Correction publishing: 10 seconds
- [ ] Timeout handling:
  - Fast failure for hotpath
  - Graceful degradation for coldpath
  - Timeout metrics and alerting

#### 8.4 Bulkhead Pattern (Resource Isolation)
- [ ] **Hotpath Isolation:**
  - Separate thread pools for hotpath operations
  - Dedicated database connection pool
  - Isolated Kafka consumer threads
  - CPU/memory limits in Kubernetes
- [ ] **Coldpath Isolation:**
  - Separate thread pools (can be larger)
  - Separate database connection pool
  - Independent Kafka consumer group
  - Different resource limits
- [ ] Thread pool configuration:
  - Hotpath: Small, high-priority pools
  - Coldpath: Larger pools, lower priority
  - Queue size limits to prevent unbounded growth

#### 8.5 Health Checks & Self-Healing
- [ ] Liveness probes:
  - Hotpath: Check Kafka consumer health
  - Coldpath: Check recalculation service health
  - Database connectivity checks
- [ ] Readiness probes:
  - Hotpath: Verify can process trades
  - Coldpath: Verify can process backdated trades
  - Dependency health (Kafka, database)
- [ ] Health check endpoints:
  - `/health`: Basic health
  - `/health/readiness`: Readiness status
  - `/health/liveness`: Liveness status
  - `/health/detailed`: Component-level health
- [ ] Self-healing mechanisms:
  - Automatic pod restarts on liveness failures
  - Circuit breaker recovery
  - Connection pool recovery
  - Consumer group rebalancing

#### 8.6 Graceful Degradation
- [ ] **Hotpath Degradation:**
  - Contract generation failure: Queue trade, continue processing
  - Database slow: Use cached snapshot, log for reconciliation
  - Kafka producer slow: Buffer locally, async publish
- [ ] **Coldpath Degradation:**
  - Recalculation slow: Increase timeout, scale out
  - Event stream large: Stream processing, chunked replay
  - Memory pressure: Spill to disk, process in batches
- [ ] Degradation strategies:
  - Feature flags for non-critical features
  - Reduced functionality modes
  - Manual intervention triggers

### Phase 9: Backpressure Management (Week 5-6)

#### 9.1 Kafka Consumer Backpressure
- [ ] **Consumer Lag Monitoring:**
  - Monitor lag per partition
  - Alert when lag exceeds threshold (e.g., 10,000 messages)
  - Track lag trends (increasing/decreasing)
- [ ] **Adaptive Consumer Throttling:**
  - Reduce `max.poll.records` when lag is high
  - Pause consumption on specific partitions if lag critical
  - Resume when lag decreases
- [ ] **Consumer Rate Limiting:**
  - Configurable max messages per second
  - Per-partition rate limits
  - Backpressure signals to producers (if possible)
- [ ] **Hotpath Consumer Backpressure:**
  - Critical: Must maintain <100ms latency
  - Pause consumption if processing queue > threshold
  - Scale out consumers when lag increases
  - Dead letter queue for messages that can't be processed
- [ ] **Coldpath Consumer Backpressure:**
  - Less critical: Can tolerate higher lag
  - Adaptive batching based on queue depth
  - Priority queue: Process newer backdated trades first

#### 9.2 Database Connection Pool Backpressure
- [ ] **Connection Pool Monitoring:**
  - Active connections
  - Idle connections
  - Wait time for connections
  - Connection acquisition failures
- [ ] **Pool Sizing Strategy:**
  - Hotpath: Smaller pool, faster acquisition
  - Coldpath: Larger pool, can wait longer
  - Dynamic sizing based on load
- [ ] **Backpressure Mechanisms:**
  - Reject requests when pool exhausted (hotpath)
  - Queue requests when pool exhausted (coldpath)
  - Timeout on connection acquisition
  - Circuit breaker on repeated failures
- [ ] **Connection Pool Configuration:**
  ```properties
  # Hotpath
  hotpath.pool.min-size=5
  hotpath.pool.max-size=20
  hotpath.pool.acquisition-timeout=50ms
  
  # Coldpath
  coldpath.pool.min-size=10
  coldpath.pool.max-size=50
  coldpath.pool.acquisition-timeout=5s
  ```

#### 9.3 Rate Limiting & Throttling
- [ ] **Request Rate Limiting:**
  - Per-position rate limits (prevent hot keys)
  - Global rate limits (prevent overload)
  - Token bucket algorithm
- [ ] **Adaptive Throttling:**
  - Increase rate when system healthy
  - Decrease rate when under pressure
  - Based on queue depth, latency, error rate
- [ ] **Priority-Based Throttling:**
  - Hotpath: Always highest priority
  - Current-dated trades: Higher priority than backdated
  - Market data: Lower priority than trades
- [ ] **Throttling Implementation:**
  - Use Guava RateLimiter or custom implementation
  - Per-operation type limits
  - Metrics for throttled requests

#### 9.4 Queue Depth Management
- [ ] **In-Memory Queue Monitoring:**
  - Hotpath processing queue depth
  - Coldpath recalculation queue depth
  - Provisional position queue
- [ ] **Queue Depth Thresholds:**
  - Hotpath: Alert at 1000, reject at 5000
  - Coldpath: Alert at 5000, scale out at 10000
  - Dead letter queue: Alert at 100
- [ ] **Queue Management Strategies:**
  - Bounded queues (prevent memory issues)
  - Queue eviction policies (FIFO, priority-based)
  - Spill to disk for coldpath queues
- [ ] **Load Shedding:**
  - Drop low-priority messages when queue full
  - Reject new requests when overloaded
  - Graceful degradation modes

#### 9.5 Adaptive Scaling
- [ ] **Kubernetes HPA (Horizontal Pod Autoscaler):**
  - Hotpath: Scale based on Kafka lag and latency
  - Coldpath: Scale based on queue depth
  - Min/max replica limits
- [ ] **Scaling Triggers:**
  - Hotpath: Kafka lag > 1000 OR latency > 80ms
  - Coldpath: Queue depth > 5000 OR processing time > 1min
  - Scale down: When metrics normalize for 5 minutes
- [ ] **Scaling Policies:**
  - Scale up: Aggressive (add 2 pods at a time)
  - Scale down: Conservative (remove 1 pod at a time)
  - Cooldown periods between scaling actions
- [ ] **Resource-Based Scaling:**
  - CPU utilization > 70%: Scale up
  - Memory pressure: Scale up or optimize
  - Network I/O: Monitor and scale if needed

#### 9.6 Backpressure Propagation
- [ ] **Upstream Backpressure Signals:**
  - Signal to Kafka producers when consumer lag high
  - Signal to trade capture system when overloaded
  - Use Kafka consumer pause/resume API
- [ ] **Downstream Backpressure Handling:**
  - Handle backpressure from contract service
  - Handle backpressure from database
  - Graceful handling of downstream failures
- [ ] **Backpressure Metrics:**
  - Consumer lag per topic/partition
  - Queue depths at each stage
  - Rejection rates
  - Throttling rates

### Phase 10: Auditing, Compliance & Reconciliation (Week 6)

#### 10.1 Event Sourcing Audit Trail
- [ ] **Audit Log via Event Store:**
  - Event Store serves as immutable audit log
  - Every event includes:
    - `correlation_id`: Trace across services
    - `causation_id`: Parent event that triggered this
    - `user_id`: Who initiated the action
    - `timestamp`: When event occurred
    - `event_version`: Schema version
  - Chronological ordering guaranteed
  - Non-repudiable history
- [ ] **Audit Event Types:**
  - `TradeAppliedEvent`: Trade processed
  - `PositionClosedEvent`: Position closed
  - `PositionCorrectedEvent`: Position corrected (coldpath)
  - `ProvisionalTradeAppliedEvent`: Provisional trade
  - `ContractGeneratedEvent`: Contract created
  - `RegulatoryReportSubmittedEvent`: Report submitted
- [ ] **Audit Query Interface:**
  - Query events by position key
  - Query events by correlation ID
  - Query events by time range
  - Query events by user
  - Export audit trail for compliance

#### 10.2 Correlation & Causation ID Tracking
- [ ] **Correlation ID Propagation:**
  - Generate correlation ID at trade ingestion
  - Propagate through all services:
    - Trade Processing Service (TPS)
    - Contract Generation Service (CGS)
    - Regulatory Reporting Service (RRS)
    - Recalculation Service (Coldpath)
  - Include in all Kafka messages
  - Include in all database writes
  - Include in all external API calls
- [ ] **Causation ID Tracking:**
  - Store parent event ID that caused this event
  - Build event causality chain
  - Trace from root cause to all effects
- [ ] **Correlation ID Implementation:**
  - Use MDC (Mapped Diagnostic Context) in Spring
  - Thread-local storage
  - Automatic propagation in async contexts
  - Include in all log statements
  - Include in distributed traces
- [ ] **Correlation ID Format:**
  - UUID v4 or similar
  - Include timestamp for debugging
  - Include service identifier

#### 10.3 Trade Reconciliation Service
- [ ] **Reconciliation Microservice:**
  - Dedicated service for position reconciliation
  - Runs on scheduled basis (e.g., hourly, daily)
  - Compares multiple sources of truth
- [ ] **Reconciliation Sources:**
  - **Internal Position**: Current state in Read Model (Snapshot)
  - **External Position**: Position from custodian/clearing house
  - **Regulatory Report**: Data submitted to Trade Repository (TR)
  - **Event Store**: Reconstructed position from events
- [ ] **Reconciliation Process:**
  1. Load internal position (from snapshot)
  2. Fetch external position (from custodian API)
  3. Fetch regulatory report (from TR)
  4. Reconstruct position from event store (for validation)
  5. Compare all sources
  6. Identify discrepancies ("Breaks")
  7. Classify break severity (Critical, Warning, Info)
  8. Trigger investigation workflow
- [ ] **Break Detection:**
  - Quantity mismatches
  - Price mismatches
  - Date mismatches
  - Missing positions
  - Extra positions
  - Tax lot count mismatches
- [ ] **Investigation Workflow:**
  - Create break ticket
  - Assign to operations team
  - Track investigation steps
  - Log resolution actions
  - Close break after resolution
  - Audit trail of all actions
- [ ] **Automated Resolution:**
  - Auto-resolve known break patterns
  - Time-based reconciliation (temporary breaks)
  - Tolerance-based reconciliation (small differences)
- [ ] **Reconciliation Reports:**
  - Daily reconciliation summary
  - Break analysis reports
  - Trend analysis
  - Compliance reports

#### 10.4 Compliance & Regulatory Reporting
- [ ] **Regulatory Event Tracking:**
  - Track all regulatory submissions
  - Store submission confirmations
  - Track submission status
  - Handle submission failures
- [ ] **Trade Repository Integration:**
  - Submit trades to TR
  - Receive submission confirmations
  - Handle rejections and corrections
  - Retry failed submissions
- [ ] **Compliance Audit Trail:**
  - Complete history of regulatory actions
  - Who submitted what and when
  - Submission status and responses
  - Correction history
- [ ] **Compliance Queries:**
  - Query by trade ID
  - Query by submission date
  - Query by status
  - Export for regulatory audits

### Phase 11: Observability & Monitoring (Week 6-7)

#### 11.1 Distributed Tracing
- [ ] **OpenTelemetry Integration:**
  - Add OpenTelemetry dependencies
  - Configure trace exporters (Jaeger, Zipkin, or commercial)
  - Instrument all services
- [ ] **Trace Instrumentation:**
  - **Hotpath Tracing:**
    - Trace: Trade Ingestion → TPS → CGS → RRS
    - Measure latency at each step
    - Identify bottlenecks
  - **Coldpath Tracing:**
    - Trace: Backdated Trade → Recalculation → Correction
    - Measure recalculation time
    - Track event replay performance
- [ ] **Span Creation:**
  - Create spans for:
    - Trade processing
    - Contract generation
    - Regulatory reporting
    - Event persistence
    - Snapshot updates
    - Event replay
    - Tax lot calculations
- [ ] **Trace Context Propagation:**
  - Propagate trace context through Kafka
  - Include trace ID in correlation ID
  - Link spans across services
  - Parent-child span relationships
- [ ] **Trace Sampling:**
  - 100% sampling for errors
  - Configurable sampling for normal traffic (e.g., 10%)
  - Adaptive sampling based on load
- [ ] **Trace Analysis:**
  - Identify slow operations
  - Find latency bottlenecks
  - Analyze error patterns
  - Service dependency mapping

#### 11.2 Metrics Collection (Micrometer)
- [ ] **Application Metrics:**
  - Trade processing rate (trades/second)
  - Trade processing latency (p50, p95, p99)
  - Current-dated vs backdated trade ratio
  - Contract generation latency
  - Regulatory reporting latency
- [ ] **Kafka Metrics:**
  - Consumer lag per topic/partition
  - Consumer throughput
  - Producer throughput
  - Message processing rate
  - DLQ message count
- [ ] **Database Metrics:**
  - Query latency
  - Connection pool utilization
  - Transaction success/failure rates
  - Snapshot hit/miss rates
- [ ] **Business Metrics:**
  - Positions processed
  - Tax lots created/closed
  - Provisional positions count
  - Reconciliation breaks detected
  - Regulatory submissions
- [ ] **Resiliency Metrics:**
  - Circuit breaker states
  - Retry attempt counts
  - Timeout occurrences
  - Health check status
- [ ] **Backpressure Metrics:**
  - Queue depths
  - Throttled requests
  - Load shedding events
  - Connection pool wait times
- [ ] **Metrics Export:**
  - Prometheus format
  - Push to Prometheus or scrape endpoint
  - Configure scrape intervals
  - Label metrics appropriately

#### 11.3 Monitoring Dashboards (Grafana)
- [ ] **Hotpath Dashboard:**
  - Trade processing rate
  - Latency percentiles (p50, p95, p99)
  - Error rates
  - Consumer lag
  - Contract generation latency
  - Circuit breaker states
- [ ] **Coldpath Dashboard:**
  - Backdated trade processing rate
  - Recalculation latency
  - Queue depth
  - Provisional to reconciled conversion time
  - Correction event publishing rate
- [ ] **System Health Dashboard:**
  - Service health status
  - Pod status and counts
  - Resource utilization (CPU, memory)
  - Database connection pools
  - Kafka topic health
- [ ] **Business Dashboard:**
  - Trades processed (daily/hourly)
  - Positions managed
  - Reconciliation breaks
  - Regulatory submissions
  - Data quality metrics
- [ ] **Alerting Dashboard:**
  - Active alerts
  - Alert history
  - Alert resolution times

#### 11.4 Alerting Rules
- [ ] **Critical Alerts (Pager Duty):**
  - Hotpath latency > 100ms p99
  - Consumer lag > 5000 messages (hotpath)
  - Circuit breaker OPEN (contract service)
  - Database connection pool exhausted
  - Service down (liveness failure)
  - High error rate (>5%)
- [ ] **Warning Alerts (Email/Slack):**
  - Hotpath latency > 80ms p95
  - Consumer lag > 1000 messages (hotpath)
  - Coldpath queue depth > 10000
  - Provisional positions not reconciled > 5 minutes
  - Reconciliation breaks detected
  - DLQ message count > 100
- [ ] **Info Alerts (Dashboard Only):**
  - Consumer lag increasing trend
  - Queue depth increasing
  - Retry rate > 10%
  - Timeout rate > 5%
- [ ] **Alert Configuration:**
  - Alert thresholds (configurable)
  - Alert grouping (reduce noise)
  - Alert routing (team assignment)
  - Alert escalation policies
  - Alert acknowledgment
  - Alert resolution tracking

#### 11.5 Logging Strategy
- [ ] **Structured Logging (JSON):**
  - All logs in JSON format
  - Consistent log structure
  - Machine-readable
- [ ] **Log Levels:**
  - ERROR: System errors, failures
  - WARN: Recoverable issues, degraded performance
  - INFO: Business events, state changes
  - DEBUG: Detailed debugging (disabled in production)
  - TRACE: Very detailed (disabled in production)
- [ ] **Log Context:**
  - Include correlation ID in all logs
  - Include causation ID
  - Include user ID
  - Include service name
  - Include trace ID
- [ ] **Log Aggregation:**
  - Centralized log collection (ELK, Splunk, etc.)
  - Log retention policy (90 days)
  - Log indexing for search
  - Log analysis and alerting
- [ ] **Sensitive Data:**
  - Do NOT log sensitive data (PII, account numbers)
  - Mask sensitive fields
  - Audit log access

#### 11.6 Performance Monitoring
- [ ] **APM Integration:**
  - Integrate with APM tool (New Relic, Datadog, etc.)
  - Application performance monitoring
  - Database query monitoring
  - External API call monitoring
- [ ] **Custom Performance Metrics:**
  - FIFO/LIFO calculation time
  - Tax lot allocation time
  - Event replay time
  - Snapshot compression/decompression time
- [ ] **Performance Baselines:**
  - Establish performance baselines
  - Track performance trends
  - Alert on performance degradation
  - Performance regression testing

### Phase 12: Core Service Implementation (Week 7)

#### 8.1 Hotpath PositionService
- [ ] Implement `processCurrentDatedTrade(TradeEvent)`:
  - Load snapshot with optimistic locking
  - Calculate expected version
  - Apply tax lot logic
  - Generate contract (synchronous)
  - Persist event (with retry on conflict)
  - Update snapshot (RECONCILED status)
  - Target latency: <100ms p99
- [ ] Implement `processBackdatedTrade(TradeEvent)`:
  - Route to coldpath topic
  - Calculate provisional position
  - Create provisional snapshot
  - Do NOT block processing
  - Target overhead: <5ms

#### 8.2 Coldpath RecalculationService
- [ ] Implement `recalculatePosition(BackdatedTradeEvent)`:
  - Load complete event stream
  - Find insertion point
  - Inject backdated trade
  - Replay events chronologically
  - Recalculate tax lots
  - Generate corrected snapshot
  - Override provisional snapshot
  - Publish correction event

#### 8.3 Transaction Management
- [ ] Configure `@Transactional` boundaries:
  - Hotpath: Synchronous transactions
  - Coldpath: Longer-running transactions (acceptable)
- [ ] Ensure event and snapshot updates are atomic
- [ ] Handle rollback scenarios
- [ ] Separate transaction managers for hotpath/coldpath if needed

#### 8.4 Compression/Decompression
- [ ] Implement `compressLots(List<TaxLot>)` → JSONB
- [ ] Implement `inflate(JSONB)` → List<TaxLot>
- [ ] Optimize for large lot counts (1000+ lots)
- [ ] Support provisional position compression

### Phase 13: Infrastructure & Deployment (Week 8)

#### 13.1 Kubernetes Resources
- [ ] StatefulSet for Hotpath service pods:
  - Replica count configuration (prioritize for low latency)
  - Resource limits (CPU, memory)
  - Resource requests (guaranteed allocation)
  - Health checks (liveness, readiness)
  - Pod disruption budgets
  - Priority classes (high priority for hotpath)
- [ ] Deployment for Coldpath service pods:
  - Independent scaling from hotpath
  - Can use different resource profiles
  - Health checks
  - Lower priority class
- [ ] Horizontal Pod Autoscaler (HPA):
  - Hotpath: Scale on Kafka lag and latency
  - Coldpath: Scale on queue depth
  - Min/max replica limits
  - Scaling policies (aggressive up, conservative down)
- [ ] Service definitions (ClusterIP, LoadBalancer if needed)
- [ ] ConfigMaps for application configuration
  - Separate configs for hotpath vs coldpath
  - Circuit breaker configuration
  - Backpressure thresholds
- [ ] Secrets for database credentials, Kafka auth

#### 13.2 Kafka Topic Configuration
- [ ] `trade-events` topic (hotpath input):
  - Partition count (16+ for parallelism)
  - Replication factor (3)
  - Retention policy
- [ ] `backdated-trades` topic (coldpath input):
  - Partition count (can be different from hotpath)
  - Replication factor (3)
  - Longer retention (for replay scenarios)
- [ ] `trade-applied-events` topic (hotpath output)
- [ ] `provisional-trade-events` topic (hotpath output)
- [ ] `historical-position-corrected-events` topic (coldpath output)

#### 13.3 Database Infrastructure
- [ ] Postgres StatefulSet (separate from service)
- [ ] Persistent volume claims
- [ ] Backup strategy
- [ ] Connection pooling configuration

#### 13.4 Schema Registry Infrastructure
- [ ] Deploy Confluent Schema Registry:
  - Standalone or managed service
  - High availability (3+ replicas)
  - Backup and recovery
- [ ] Schema Registry Configuration:
  - Compatibility mode (BACKWARD, FORWARD, FULL)
  - Schema retention policy
  - Authentication and authorization
- [ ] Schema Registry Monitoring:
  - Schema registry health
  - Schema version counts
  - Schema evolution metrics

#### 13.5 Monitoring & Observability Infrastructure
- [ ] Metrics: Prometheus integration
  - **Hotpath metrics:**
    - Trade processing latency (p50, p95, p99)
    - Current-dated vs backdated trade ratio
    - Contract generation latency
    - Snapshot update success rate
  - **Coldpath metrics:**
    - Backdated trade processing latency
    - Recalculation queue depth
    - Correction event publication rate
    - Provisional to reconciled conversion time
  - **System metrics:**
    - Provisional position count
    - Pending reconciliations
    - Correction event lag
  - **Resiliency metrics:**
    - Circuit breaker states and transitions
    - Retry attempt counts and success rates
    - Timeout occurrences and rates
    - Health check status
  - **Backpressure metrics:**
    - Consumer lag per topic/partition
    - Queue depths (hotpath, coldpath, DLQ)
    - Connection pool utilization
    - Rate limiting events
    - Throttled requests
    - Load shedding events
- [ ] Logging: Structured logging (JSON)
  - Correlation ID tracking
  - Event version tracking
  - Hotpath vs coldpath tagging
- [ ] Distributed tracing: OpenTelemetry/Jaeger
  - Trace hotpath and coldpath separately
  - Track provisional to reconciled flow

#### 13.6 Resiliency & Backpressure Monitoring
- [ ] Circuit breaker metrics:
  - State transitions (CLOSED → OPEN → HALF_OPEN)
  - Failure rates
  - Call counts
- [ ] Retry metrics:
  - Retry attempts per operation
  - Retry success/failure rates
  - Retry latency
- [ ] Timeout metrics:
  - Timeout occurrences
  - Timeout rates by operation
  - Timeout impact on latency
- [ ] Backpressure metrics:
  - Consumer lag per topic/partition
  - Queue depths
  - Connection pool utilization
  - Rate limiting events
- [ ] Health check metrics:
  - Health check failures
  - Component health status
  - Recovery times
- [ ] Alerting rules:
  - Circuit breaker opened
  - High retry rates
  - Timeout spikes
  - Consumer lag thresholds
  - Queue depth thresholds
  - Connection pool exhaustion

### Phase 14: Testing (Week 8-9)

#### 14.1 Unit Tests
- [ ] Tax lot logic (FIFO/LIFO)
- [ ] Compression/decompression
- [ ] Trade classification logic
- [ ] Event routing logic (hotpath vs coldpath)
- [ ] Version conflict handling
- [ ] Provisional position logic
- [ ] Event replay logic

#### 14.2 Integration Tests
- [ ] End-to-end hotpath processing (current-dated trades)
- [ ] End-to-end coldpath processing (backdated trades)
- [ ] Provisional position creation and correction
- [ ] Database transactions
- [ ] Kafka consumer/producer (both paths)
- [ ] Optimistic locking scenarios
- [ ] Contract generation (synchronous and async)

#### 14.3 Performance Tests
- [ ] **Hotpath performance:**
  - Load testing: 2M trades/day target
  - Latency testing: <100ms p99 for current-dated trades
  - Backdated trade routing overhead (<5ms)
- [ ] **Coldpath performance:**
  - Recalculation latency for various event stream sizes
  - Queue depth under load
  - Correction event publishing rate
- [ ] Concurrency testing: Multiple consumers on same partition
- [ ] Snapshot performance: Large lot counts (1000+)
- [ ] Database partition performance

#### 14.4 Data Quality & Schema Testing
- [ ] Schema evolution testing:
  - Test backward compatibility
  - Test forward compatibility
  - Test schema migration
  - Test old consumers with new schemas
  - Test new consumers with old schemas
- [ ] Validation testing:
  - Test all validation rules
  - Test DLQ routing
  - Test error queue handling
  - Test validation performance
- [ ] Idempotency testing:
  - Test duplicate trade processing
  - Test idempotency key storage
  - Test idempotency cleanup
  - Test idempotency in concurrent scenarios

#### 14.5 Auditing & Reconciliation Testing
- [ ] Audit trail testing:
  - Verify all events are audited
  - Test audit query interface
  - Test correlation ID propagation
  - Test causation ID tracking
- [ ] Reconciliation testing:
  - Test reconciliation process
  - Test break detection
  - Test investigation workflow
  - Test automated resolution
- [ ] Compliance testing:
  - Test regulatory reporting
  - Test submission tracking
  - Test compliance queries

#### 14.6 Observability Testing
- [ ] Distributed tracing testing:
  - Verify trace propagation
  - Test trace sampling
  - Test trace analysis
- [ ] Metrics testing:
  - Verify all metrics are collected
  - Test metrics export
  - Test metrics accuracy
- [ ] Logging testing:
  - Test structured logging
  - Test correlation ID in logs
  - Test log aggregation

#### 14.7 Resiliency Testing
- [ ] Circuit breaker testing:
  - Simulate dependency failures
  - Verify circuit opens/closes correctly
  - Test fallback mechanisms
- [ ] Retry testing:
  - Test exponential backoff
  - Test max retry limits
  - Test jitter application
- [ ] Timeout testing:
  - Simulate slow dependencies
  - Verify timeout handling
  - Test timeout propagation
- [ ] Backpressure testing:
  - Simulate high Kafka lag
  - Test consumer throttling
  - Test queue depth management
  - Test load shedding
- [ ] Resource exhaustion testing:
  - Connection pool exhaustion
  - Memory pressure
  - CPU throttling
- [ ] Recovery testing:
  - Test recovery after failures
  - Test circuit breaker recovery
  - Test connection pool recovery

#### 14.8 Chaos Testing
- [ ] Network partitions (hotpath and coldpath separately)
- [ ] Database failures
- [ ] Kafka broker failures
- [ ] Pod restarts (hotpath and coldpath)
- [ ] Coldpath failures (should not affect hotpath)
- [ ] Provisional position reconciliation delays

### Phase 15: Documentation & Handoff (Week 9)

#### 15.1 Technical Documentation
- [ ] API documentation (if REST endpoints exist)
- [ ] Architecture decision records (ADRs)
  - Hotpath/coldpath separation decision
  - Provisional position strategy
  - Contract generation approach
  - Schema evolution strategy
  - Data validation approach
  - Idempotency strategy
  - Reconciliation approach
  - Observability strategy
- [ ] Runbook for operations
  - Hotpath operations
  - Coldpath operations
  - Provisional position reconciliation procedures
  - Data validation and DLQ handling
  - Schema evolution procedures
  - Reconciliation break resolution
  - Audit trail queries
  - Observability and troubleshooting
- [ ] Database schema documentation

#### 15.2 Operational Documentation
- [ ] Deployment procedures
  - Hotpath deployment
  - Coldpath deployment
  - Independent scaling procedures
- [ ] Monitoring dashboards
  - Hotpath dashboard
  - Coldpath dashboard
  - Provisional position monitoring
- [ ] Alerting rules
  - Hotpath latency alerts (>80ms p95)
  - Coldpath queue depth alerts (>5000)
  - Unreconciled provisional positions (>5 minutes)
  - Circuit breaker opened alerts
  - Consumer lag alerts (>1000 messages)
  - Connection pool exhaustion alerts
  - High retry rate alerts (>10% retries)
  - Timeout spike alerts
  - DLQ message count alerts (>100)
  - Reconciliation break alerts
  - Schema compatibility failures
  - Validation failure rate alerts
  - Distributed trace errors
- [ ] Disaster recovery procedures
  - Hotpath recovery
  - Coldpath recovery
  - Data consistency procedures

## Technical Decisions & Considerations

### 1. Partitioning Strategy
- **Hash-based partitioning** on `position_key` ensures even distribution
- 16 partitions provide good balance between parallelism and overhead
- Consider dynamic partition creation if needed

### 2. Optimistic Locking
- Version-based conflict detection
- Retry mechanism with exponential backoff
- Consider circuit breaker for repeated failures

### 3. Snapshot Strategy
- **Fat Snapshot**: Store all lot details in snapshot
- Alternative: **Thin Snapshot** with event replay for large positions
- Decision point: When to switch strategies based on lot count
- **Provisional Snapshots**: Temporary snapshots for backdated trades until coldpath reconciliation

### 4. Hotpath/Coldpath Architecture
- **Separation of Concerns**: Real-time processing (hotpath) vs historical recalculation (coldpath)
- **Trade Classification**: Automatic routing based on effective date vs snapshot date
- **Provisional Positions**: Temporary "dirty" positions until coldpath correction
- **Eventual Consistency**: Read model may show provisional positions temporarily
- **Independent Scaling**: Hotpath and coldpath can scale independently
- **Resource Isolation**: Coldpath failures don't affect hotpath performance

### 5. Compression Format
- Parallel arrays reduce JSON size significantly
- Consider binary formats (Avro, Protobuf) for further optimization
- Balance between storage size and query complexity

### 6. Kafka Consumer Strategy
- **Hotpath**: One consumer per partition for maximum parallelism
- **Coldpath**: Can use different consumer group strategy (less latency-sensitive)
- Manual offset management for exactly-once semantics
- Consider idempotency keys for additional safety
- Separate consumer groups for hotpath and coldpath

### 7. Database Connection Pooling
- Size pool based on concurrent consumers
- **Hotpath**: Prioritize low-latency connections
- **Coldpath**: Can use different pool configuration (longer transactions OK)
- Separate pools for read/write if using replicas
- Monitor connection pool metrics separately for hotpath/coldpath

### 8. Resource Allocation
- **Hotpath**: High-priority CPU/memory allocation for low latency
- **Coldpath**: Can use lower-priority resources, scale independently
- Separate Kubernetes resource requests/limits
- Independent auto-scaling policies

### 9. Resiliency Patterns
- **Circuit Breakers**: Prevent cascading failures
- **Retry Strategies**: Exponential backoff with jitter
- **Timeouts**: Fail-fast for hotpath, graceful for coldpath
- **Bulkhead Pattern**: Resource isolation between hotpath/coldpath
- **Health Checks**: Liveness and readiness probes
- **Graceful Degradation**: Reduced functionality under load

### 10. Backpressure Management
- **Kafka Consumer Backpressure**: Adaptive throttling based on lag
- **Connection Pool Backpressure**: Reject/queue when exhausted
- **Rate Limiting**: Per-position and global limits
- **Queue Depth Management**: Bounded queues with eviction
- **Adaptive Scaling**: HPA based on metrics
- **Load Shedding**: Drop low-priority messages when overloaded

## Risk Mitigation

### High-Risk Areas

1. **Concurrency Issues**
   - Mitigation: Comprehensive testing of optimistic locking
   - Load testing with realistic concurrency patterns
   - Separate locking strategies for hotpath vs coldpath

2. **Performance at Scale**
   - Mitigation: Early performance testing
   - Database partition tuning
   - Snapshot cache optimization
   - **Hotpath**: Prioritize low-latency resources
   - **Coldpath**: Scale independently, can tolerate higher latency

3. **Data Consistency**
   - Mitigation: Transaction boundaries
   - Event versioning
   - Audit logging
   - **Provisional Positions**: Clear reconciliation SLA
   - **Eventual Consistency**: Document acceptable delay windows

4. **Kafka Lag**
   - Mitigation: Consumer group monitoring
   - Auto-scaling based on lag metrics
   - Dead letter queue handling
   - **Hotpath**: Critical - monitor closely
   - **Coldpath**: Less critical - can tolerate some lag

5. **Coldpath Failures**
   - Mitigation: Coldpath failures should NOT affect hotpath
   - Independent resource allocation
   - Dead letter queue for failed recalculations
   - Alerting for unreconciled provisional positions

6. **Provisional Position Staleness**
   - Mitigation: SLA for reconciliation time
   - Monitoring for provisional positions not reconciled
   - Alerting for positions stuck in PROVISIONAL status
   - Manual reconciliation procedures

7. **Cascading Failures**
   - Mitigation: Circuit breakers on all external dependencies
   - Bulkhead pattern isolates failures
   - Timeout management prevents hanging requests
   - Graceful degradation maintains core functionality

8. **Backpressure & Overload**
   - Mitigation: Consumer lag monitoring and throttling
   - Connection pool backpressure handling
   - Rate limiting prevents overload
   - Adaptive scaling based on metrics
   - Load shedding for non-critical operations

9. **Resource Exhaustion**
   - Mitigation: Bounded queues prevent memory issues
   - Connection pool limits prevent database overload
   - CPU/memory limits in Kubernetes
   - Resource monitoring and alerting
   - Automatic scaling on resource pressure

10. **Data Quality Issues**
    - Mitigation: Validation gate prevents bad data
    - DLQ for manual review
    - Schema validation ensures compatibility
    - Idempotency prevents duplicate processing
    - Comprehensive validation rules

11. **Schema Evolution Issues**
    - Mitigation: Schema Registry manages evolution
    - Compatibility modes ensure backward/forward compatibility
    - Versioning strategy for controlled changes
    - Testing of schema migrations
    - Rollback procedures

12. **Audit & Compliance Gaps**
    - Mitigation: Event Store as immutable audit log
    - Correlation ID propagation
    - Reconciliation service detects breaks
    - Complete audit trail
    - Compliance reporting tracking

## Success Criteria

### Hotpath Performance
- [ ] Process 2M trades/day with <100ms p99 latency for current-dated trades
- [ ] Backdated trade routing overhead <5ms
- [ ] Contract generation latency <50ms (synchronous)
- [ ] Zero blocking of hotpath by coldpath operations

### Coldpath Performance
- [ ] Recalculate backdated trades within SLA (e.g., <5 minutes for typical positions)
- [ ] Handle event streams with 1000+ events per position
- [ ] Provisional to reconciled conversion within SLA
- [ ] Independent scaling without affecting hotpath

### Data Consistency
- [ ] Zero data loss (eventual consistency acceptable for provisional positions)
- [ ] All provisional positions eventually reconciled
- [ ] Support positions with 1000+ tax lots
- [ ] Handle concurrent updates without data corruption

### System Reliability
- [ ] 99.9% uptime SLA for hotpath
- [ ] Coldpath failures do not impact hotpath availability
- [ ] Independent deployment and scaling of hotpath/coldpath

### Data Quality
- [ ] 100% validation of incoming trades
- [ ] Zero invalid trades in main processing flow
- [ ] All failed trades routed to DLQ
- [ ] Schema compatibility maintained across versions
- [ ] Idempotency prevents duplicate processing

### Audit & Compliance
- [ ] Complete audit trail in Event Store
- [ ] Correlation ID propagation across all services
- [ ] Daily reconciliation with external sources
- [ ] All regulatory submissions tracked
- [ ] Audit queries return complete history

### Observability
- [ ] 100% of errors traced end-to-end
- [ ] All critical metrics collected
- [ ] Dashboards show real-time system health
- [ ] Alerts trigger within 1 minute of threshold breach
- [ ] Logs include correlation IDs for traceability

### Resiliency
- [ ] Circuit breakers prevent cascading failures
- [ ] Automatic recovery from transient failures
- [ ] Graceful degradation under load
- [ ] Health checks enable self-healing
- [ ] Zero data loss during failures (events persisted)

### Backpressure Management
- [ ] Consumer lag < 1000 messages (hotpath)
- [ ] Queue depths within thresholds
- [ ] Connection pool utilization < 80%
- [ ] Automatic scaling based on load
- [ ] Load shedding prevents system overload

## Timeline Summary

| Phase | Duration | Key Deliverables |
|-------|----------|------------------|
| Phase 1: Foundation | 1 week | Project structure, dependencies |
| Phase 2: Database | 1 week | Schema, migrations, repositories |
| Phase 3: Domain Models | 1 week | Entities, domain objects |
| Phase 4: Tax Lot Engine | 1 week | FIFO/LIFO logic, contract rules |
| Phase 5: Kafka Integration | 1 week | Consumers, error handling, topics |
| Phase 6: Hotpath Implementation | 1-2 weeks | Trade classification, hotpath handler, provisional positions |
| Phase 7: Coldpath Implementation | 1-2 weeks | Recalculation service, event replay, corrections |
| Phase 8: Resiliency & Backpressure | 1 week | Circuit breakers, retries, timeouts, bulkheads |
| Phase 9: Backpressure Management | 1 week | Consumer backpressure, rate limiting, adaptive scaling |
| Phase 10: Auditing, Compliance & Reconciliation | 1 week | Audit trail, reconciliation service, correlation IDs |
| Phase 11: Observability & Monitoring | 1-2 weeks | Distributed tracing, metrics, dashboards, alerting |
| Phase 12: Core Service | 1 week | PositionService integration, transactions |
| Phase 13: Infrastructure | 1 week | K8s resources, monitoring, Kafka topics, Schema Registry |
| Phase 14: Testing | 1-2 weeks | Unit, integration, performance, data quality, observability tests |
| Phase 15: Documentation | 1 week | Technical and operational docs |

**Total Estimated Duration: 15-17 weeks**

## Architecture Decisions

### Why Hotpath/Coldpath Separation?
1. **Performance**: Backdated trades require full event stream replay, which is expensive
2. **Latency**: Synchronous recalculation would block real-time processing
3. **Scalability**: Independent scaling allows optimization for different workloads
4. **Resilience**: Coldpath failures don't impact hotpath availability
5. **Regulatory**: Current-dated trades need immediate contract generation and reporting

### Provisional Position Strategy
- **Temporary**: Provisional positions are estimates until coldpath reconciliation
- **Risk Calculations**: Can use provisional positions with caveats
- **Regulatory Reporting**: Should wait for reconciled positions when possible
- **SLA**: Define maximum time for provisional positions (e.g., 5 minutes)

### Contract Generation Approach
- **Hotpath**: Synchronous for current/forward-dated trades (regulatory requirement)
- **Coldpath**: Asynchronous for backdated trades affecting open positions
- **Closed Positions**: Skip contract generation for backdated trades on closed positions

### Data Quality & Schema Governance
- **Schema Registry**: Confluent Schema Registry with Avro/Protobuf for schema evolution
- **Validation Gate**: Early-stage validation prevents dirty data from entering system
- **Dead Letter Queue**: Failed trades routed for manual review
- **Idempotency**: Trade ID/USI as unique key prevents duplicate processing
- **Schema Evolution**: Backward/forward compatibility ensures system stability

### Auditing, Compliance & Reconciliation
- **Event Store as Audit Log**: Immutable, chronological history of all events
- **Correlation IDs**: Trace single trade through entire distributed workflow
- **Causation IDs**: Build event causality chains for debugging
- **Reconciliation Service**: Compare internal/external/regulatory positions, detect breaks
- **Compliance Tracking**: Track all regulatory submissions and responses

### Observability & Monitoring
- **Distributed Tracing**: OpenTelemetry traces entire trade lifecycle (ingestion → TPS → CGS → RRS)
- **Metrics**: Micrometer collects custom metrics (consumer lag, FIFO calculation time, circuit breaker state)
- **Dashboards**: Grafana visualizes hotpath/coldpath health in real-time
- **Alerting**: Multi-level alerts (Critical: pager, Warning: email/Slack, Info: dashboard)
- **Structured Logging**: JSON logs with correlation IDs for traceability

### Resiliency & Backpressure Strategy
- **Circuit Breakers**: Protect against cascading failures from external dependencies
- **Retry with Exponential Backoff**: Handle transient failures gracefully
- **Timeouts**: Fail-fast for hotpath, graceful for coldpath
- **Bulkhead Pattern**: Complete resource isolation between hotpath and coldpath
- **Consumer Backpressure**: Adaptive throttling based on Kafka lag
- **Rate Limiting**: Prevent overload from hot keys or traffic spikes
- **Adaptive Scaling**: Automatic scaling based on queue depth, latency, and lag
- **Load Shedding**: Drop low-priority messages when system overloaded
- **Health Checks**: Enable Kubernetes self-healing and graceful shutdowns

## Next Steps

1. Review and approve this implementation plan
2. Set up development environment
3. Initialize project structure (Phase 1)
4. Begin database schema implementation (Phase 2)
5. Review hotpath/coldpath architecture document for detailed design

## Resiliency & Backpressure Summary

### Resiliency Patterns Implemented

1. **Circuit Breakers**
   - Protect against cascading failures
   - Applied to: Contract Service, Database, Kafka producers
   - States: CLOSED → OPEN → HALF_OPEN
   - Automatic recovery testing

2. **Retry Strategies**
   - Exponential backoff with jitter
   - Hotpath: Fast retries (<200ms total)
   - Coldpath: More retries, longer backoff
   - Configurable per operation type

3. **Timeouts**
   - Hotpath: Aggressive timeouts (50ms DB, 40ms contract)
   - Coldpath: Longer timeouts (5s-5min)
   - Fail-fast for hotpath, graceful for coldpath

4. **Bulkhead Pattern**
   - Complete resource isolation
   - Separate thread pools, connection pools
   - Independent scaling and resource limits
   - Hotpath failures don't affect coldpath

5. **Health Checks**
   - Liveness: Service is running
   - Readiness: Service can process requests
   - Component-level health monitoring
   - Kubernetes self-healing

6. **Graceful Degradation**
   - Reduced functionality under load
   - Fallback mechanisms
   - Queue for later processing
   - Manual intervention triggers

### Backpressure Management

1. **Kafka Consumer Backpressure**
   - Monitor consumer lag per partition
   - Adaptive throttling (reduce poll.records)
   - Pause/resume partitions
   - Scale out when lag high

2. **Connection Pool Backpressure**
   - Monitor pool utilization
   - Reject (hotpath) or queue (coldpath) when exhausted
   - Timeout on acquisition
   - Circuit breaker on repeated failures

3. **Rate Limiting**
   - Per-position limits (prevent hot keys)
   - Global rate limits
   - Token bucket algorithm
   - Priority-based throttling

4. **Queue Depth Management**
   - Bounded queues prevent memory issues
   - Alert at thresholds
   - Load shedding when full
   - Spill to disk for coldpath

5. **Adaptive Scaling**
   - HPA based on metrics
   - Hotpath: Scale on lag and latency
   - Coldpath: Scale on queue depth
   - Aggressive scale-up, conservative scale-down

6. **Load Shedding**
   - Drop low-priority messages when overloaded
   - Reject new requests when queue full
   - Graceful degradation modes
   - Preserve hotpath performance

### Key Metrics & Thresholds

**Hotpath:**
- Consumer lag: Alert >1000, Critical >5000
- Latency: Alert >80ms p95, Critical >100ms p99
- Queue depth: Alert >1000, Reject >5000
- Connection pool: Alert >80%, Reject >95%

**Coldpath:**
- Consumer lag: Alert >5000, Critical >10000
- Queue depth: Alert >5000, Scale out >10000
- Recalculation time: Alert >1min, Critical >5min
- Connection pool: Alert >80%, Queue >95%

**Resiliency:**
- Circuit breaker: Alert on OPEN state
- Retry rate: Alert >10%, Critical >20%
- Timeout rate: Alert >5%, Critical >10%
- Health check failures: Immediate alert
