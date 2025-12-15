# Processing Partitioning Design

## Overview

The Position Management Service uses a **multi-level partitioning strategy** to enable parallel processing and high throughput:

1. **Kafka Topic Partitioning**: Messages distributed across Kafka partitions
2. **Consumer Group Partitioning**: Multiple consumers process different partitions
3. **Database Partitioning**: Events stored in hash-partitioned database tables
4. **Position Key-Based Routing**: Ensures same position processed sequentially

## Architecture Layers

### Layer 1: Kafka Topic Partitioning

**Kafka Topics** are partitioned to enable parallel consumption:

```
trade-events topic:
├── Partition 0
├── Partition 1
├── Partition 2
├── ...
└── Partition N

backdated-trades topic:
├── Partition 0
├── Partition 1
├── Partition 2
├── ...
└── Partition N
```

**Partition Assignment**:
- Messages are distributed across partitions based on the **message key** (position_key)
- Same `position_key` always goes to the same Kafka partition
- Ensures ordering for events within the same position

### Layer 2: Consumer Group Partitioning

**Consumer Groups** distribute partitions across multiple consumer instances:

```
Consumer Group: position-service-group
├── Consumer Instance 1 → Processes Kafka Partitions [0, 1, 2, 3]
├── Consumer Instance 2 → Processes Kafka Partitions [4, 5, 6, 7]
├── Consumer Instance 3 → Processes Kafka Partitions [8, 9, 10, 11]
└── Consumer Instance 4 → Processes Kafka Partitions [12, 13, 14, 15]
```

**Key Properties**:
- Each Kafka partition is consumed by **exactly one consumer** in the group
- Multiple consumers can process different partitions **in parallel**
- Kafka automatically rebalances partitions when consumers join/leave

### Layer 3: Database Partitioning

**Event Store** is hash-partitioned by `position_key`:

```
event_store (partitioned table)
├── event_store_p0 (MODULUS 16, REMAINDER 0)
├── event_store_p1 (MODULUS 16, REMAINDER 1)
├── ...
└── event_store_p15 (MODULUS 16, REMAINDER 15)
```

**Partition Mapping**:
- Same `position_key` → Same database partition
- Same `position_key` → Same Kafka partition (if key is used)
- **Alignment**: Ideally, Kafka partition assignment should align with database partitioning

## Current Implementation

### Kafka Consumer Configuration

**Hotpath Consumer** (`TradeEventConsumer`):
```java
@KafkaListener(
    topics = "trade-events",
    groupId = "position-service-group",
    containerFactory = "kafkaListenerContainerFactory"
)
```

**Coldpath Consumer** (`BackdatedTradeConsumer`):
```java
@KafkaListener(
    topics = "backdated-trades",
    groupId = "coldpath-recalculation-group",
    containerFactory = "kafkaListenerContainerFactory"
)
```

### Concurrency Configuration

**Current State**: 
- **No explicit concurrency** configured in `KafkaConfig`
- Default: **1 thread per consumer instance**
- Multiple instances can run in parallel (horizontal scaling)

**Container Factory**:
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    // No setConcurrency() call - defaults to 1
    return factory;
}
```

### Message Key Strategy

**Producer** (`TradeEventProducer`):
```java
kafkaTemplate.send("trade-events", tradeEvent.getPositionKey(), jsonPayload);
```

**Key = position_key**: Ensures all events for the same position go to the same Kafka partition

## Processing Flow

### Hotpath Processing Flow

```
1. Trade arrives → Kafka Producer
   └─> Key: position_key → Routes to specific Kafka partition

2. Kafka Consumer (position-service-group)
   └─> Receives message from assigned partition
   └─> Deserializes TradeEvent

3. TradeProcessingService.processTrade()
   └─> Validates trade
   └─> Checks idempotency
   └─> Classifies trade (current/forward/backdated)

4. HotpathPositionService.processCurrentDatedTrade()
   └─> Loads snapshot (from database partition)
   └─> Applies trade logic
   └─> Saves event (to database partition)
   └─> Updates snapshot

5. Database writes
   └─> Event → event_store_pX (based on position_key hash)
   └─> Snapshot → snapshot_store (single table, no partitioning)
```

### Coldpath Processing Flow

```
1. Backdated trade → Kafka Producer
   └─> Key: position_key → Routes to specific Kafka partition

2. Kafka Consumer (coldpath-recalculation-group)
   └─> Receives message from assigned partition
   └─> Deserializes TradeEvent

3. RecalculationService.recalculatePosition()
   └─> Loads all events for position (from database partition)
   └─> Inserts backdated trade in chronological order
   └─> Replays all events
   └─> Creates corrected snapshot
   └─> Saves to database partition
```

## Parallel Processing Capabilities

### 1. **Inter-Position Parallelism** ✅

Different positions can be processed **in parallel**:
- Position A events → Kafka Partition 0 → Consumer 1
- Position B events → Kafka Partition 1 → Consumer 2
- Position C events → Kafka Partition 2 → Consumer 3

**Result**: High throughput for multiple positions

### 2. **Intra-Position Sequential Processing** ✅

Events for the same position are processed **sequentially**:
- Position A, Event 1 → Processed first
- Position A, Event 2 → Processed after Event 1 completes
- Position A, Event 3 → Processed after Event 2 completes

**Result**: Maintains event ordering and consistency

### 3. **Database Partition Parallelism** ✅

Different database partitions can be written to **in parallel**:
- Position A → event_store_p5 → Write 1
- Position B → event_store_p12 → Write 2 (parallel)
- Position C → event_store_p3 → Write 3 (parallel)

**Result**: No database contention between different positions

## Scaling Strategies

### Horizontal Scaling (Recommended)

**Add more consumer instances**:
```
Instance 1: Processes Kafka Partitions [0-3]
Instance 2: Processes Kafka Partitions [4-7]
Instance 3: Processes Kafka Partitions [8-11]
Instance 4: Processes Kafka Partitions [12-15]
```

**Benefits**:
- Linear scaling with number of instances
- Automatic partition rebalancing
- Fault tolerance (if one instance fails, others take over)

### Vertical Scaling (Limited)

**Increase concurrency per consumer**:
```java
factory.setConcurrency(4); // 4 threads per consumer instance
```

**Considerations**:
- More threads = more database connections needed
- Thread contention for same position (if not handled)
- Database connection pool limits

**Current Limitation**: Not configured - defaults to 1 thread per consumer

## Partition Alignment

### Ideal Alignment

For optimal performance, align Kafka partitions with database partitions:

```
Kafka Partition 0 → Database Partition p0 (position_key hash % 16 = 0)
Kafka Partition 1 → Database Partition p1 (position_key hash % 16 = 1)
...
Kafka Partition 15 → Database Partition p15 (position_key hash % 16 = 15)
```

**Benefits**:
- Consumer always writes to the same database partition
- Better cache locality
- Reduced cross-partition queries

**Current State**: 
- Kafka partitions: Configurable (default depends on topic creation)
- Database partitions: Fixed at 16
- Alignment: Not explicitly enforced (relies on hash function consistency)

## Configuration Recommendations

### 1. Kafka Topic Partition Count

**Recommendation**: Match database partition count (16)

```bash
# Create topic with 16 partitions
kafka-topics --create \
  --topic trade-events \
  --partitions 16 \
  --replication-factor 3 \
  --bootstrap-server localhost:9092
```

### 2. Consumer Concurrency

**Current**: 1 thread per consumer (default)

**Option A: Keep at 1** (Recommended for now)
- Simple, predictable
- Relies on horizontal scaling
- No thread contention issues

**Option B: Increase to 2-4 threads**
```java
factory.setConcurrency(4); // 4 threads per consumer instance
```
- More parallelism per instance
- Requires careful handling of position-level locking
- May need larger connection pool

### 3. Consumer Group Size

**Recommendation**: Match or exceed Kafka partition count

```
Kafka Partitions: 16
Consumer Instances: 16 (one per partition, optimal)
                 or 8 (2 partitions per consumer)
                 or 4 (4 partitions per consumer)
```

### 4. Database Connection Pool

**Current**: 20 connections (HikariCP)

**Scaling Calculation**:
- 16 Kafka partitions
- 4 consumer instances (4 partitions each)
- 4 threads per consumer = 16 threads
- Each thread may need 1-2 connections
- **Recommended**: 32-40 connections for high concurrency

## Processing Guarantees

### Ordering Guarantees

1. **Within Position**: ✅ **Strictly Ordered**
   - Same `position_key` → Same Kafka partition → Sequential processing
   - Database writes maintain version sequence

2. **Across Positions**: ❌ **No Ordering Guarantee**
   - Different positions processed in parallel
   - No cross-position ordering required

### Consistency Guarantees

1. **Eventual Consistency**: ✅
   - Hotpath: Immediate consistency for current trades
   - Coldpath: Eventual consistency for backdated trades

2. **Strong Consistency per Position**: ✅
   - All events for a position are in the same partition
   - Sequential processing ensures consistency

## Monitoring Processing Partitioning

### Kafka Consumer Lag

Monitor lag per partition:
```bash
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group position-service-group \
  --describe
```

**Metrics to Watch**:
- `LAG` per partition (should be low)
- `CURRENT-OFFSET` vs `LOG-END-OFFSET`
- Uneven lag indicates partition imbalance

### Database Partition Metrics

Monitor writes per partition:
```sql
SELECT 
    'event_store_p' || (hashtext(position_key) % 16) as partition,
    COUNT(*) as event_count,
    COUNT(DISTINCT position_key) as position_count
FROM event_store
GROUP BY partition
ORDER BY partition;
```

### Processing Throughput

Monitor processing rate:
- Events processed per second per partition
- Average processing time per event
- P95/P99 latency per partition

## Current Limitations and Improvements

### Current Limitations

1. **No Explicit Concurrency**: Defaults to 1 thread per consumer
2. **No Partition Alignment**: Kafka and database partitions not explicitly aligned
3. **No Dynamic Scaling**: Fixed number of consumers
4. **No Partition-Aware Routing**: All consumers connect to same database

### Recommended Improvements

1. **Add Concurrency Configuration**:
   ```yaml
   spring:
     kafka:
       listener:
         concurrency: 4  # 4 threads per consumer instance
   ```

2. **Partition-Aware Connection Pooling**:
   - Route database connections based on partition
   - Partition-specific connection pools

3. **Dynamic Consumer Scaling**:
   - Kubernetes HPA based on consumer lag
   - Auto-scale consumers based on load

4. **Partition Monitoring**:
   - Alert on partition imbalance
   - Monitor per-partition metrics
   - Track hot partitions

## Summary

**Processing Partitioning Strategy**:

1. **Kafka Level**: Messages partitioned by `position_key` across topic partitions
2. **Consumer Level**: Multiple consumer instances process different partitions in parallel
3. **Database Level**: Events stored in hash-partitioned tables (16 partitions)
4. **Processing Level**: Sequential per position, parallel across positions

**Key Design Principles**:
- ✅ Same position → Same Kafka partition → Sequential processing
- ✅ Different positions → Different partitions → Parallel processing
- ✅ Database partitioning aligns with Kafka partitioning (via hash)
- ✅ Horizontal scaling through multiple consumer instances

**Current State**:
- ✅ Partitioning infrastructure in place
- ⚠️ Concurrency not explicitly configured (defaults to 1)
- ⚠️ Partition alignment not explicitly enforced
- ✅ Processing works correctly with current setup
