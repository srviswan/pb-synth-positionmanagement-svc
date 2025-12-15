# Partitioning Design for Position Management Service

## Overview

The Position Management Service uses **hash partitioning** on the event store to distribute data across multiple partitions for improved performance, scalability, and parallel processing capabilities.

## Partitioning Strategy

### 1. Hash Partitioning by Position Key

The event store is partitioned using **PostgreSQL hash partitioning** based on the `position_key` column.

**Key Design Decision**: All events for the same position are stored in the same partition, ensuring:
- **Sequential ordering** is maintained within a partition
- **Efficient queries** for position-specific event streams
- **No cross-partition queries** needed for replay
- **Parallel processing** across different positions

### 2. Database Schema

```sql
-- Main partitioned table
CREATE TABLE event_store (
    position_key    VARCHAR(64) NOT NULL,
    event_ver       BIGINT NOT NULL,
    event_type      VARCHAR(30) NOT NULL,
    effective_date  DATE NOT NULL,
    occurred_at     TIMESTAMPTZ DEFAULT NOW(),
    payload         JSONB NOT NULL,
    meta_lots       JSONB,
    correlation_id  VARCHAR(128),
    causation_id    VARCHAR(128),
    contract_id     VARCHAR(64),
    user_id         VARCHAR(64),
    
    PRIMARY KEY (position_key, event_ver)
) PARTITION BY HASH (position_key);
```

### 3. Partition Configuration

**Number of Partitions**: 16 partitions (p0 through p15)

```sql
-- 16 hash partitions
CREATE TABLE event_store_p0 PARTITION OF event_store 
    FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE event_store_p1 PARTITION OF event_store 
    FOR VALUES WITH (MODULUS 16, REMAINDER 1);
-- ... p2 through p15
```

**Hash Function**: PostgreSQL's built-in hash function on `position_key`
- **Modulus**: 16 (number of partitions)
- **Remainder**: 0-15 (partition assignment)

### 4. How Partition Assignment Works

PostgreSQL automatically calculates the partition for each row:

```sql
-- PostgreSQL internally does:
partition_number = hash(position_key) % 16
```

**Example**:
- `position_key = "ABC123"` → hash → `12345` → `12345 % 16 = 9` → **Partition p9**
- `position_key = "XYZ789"` → hash → `67890` → `67890 % 16 = 2` → **Partition p2`

### 5. Position Key Format

The `position_key` is a hash of account + instrument + currency:
```
position_key = Hash(Account + Instrument + Currency)
```

This ensures:
- **Deterministic partitioning**: Same position always goes to same partition
- **Even distribution**: Hash function distributes positions evenly across partitions
- **No hot spots**: Unless there's a specific position with extremely high volume

## Benefits of This Design

### 1. **Sequential Ordering Guarantee**
- All events for a position are in the same partition
- Event version (`event_ver`) is sequential within a partition
- No need to query across partitions for position replay

### 2. **Query Performance**
- **Position-specific queries**: Only query one partition
- **Partition pruning**: PostgreSQL automatically prunes irrelevant partitions
- **Index efficiency**: Indexes are per-partition, smaller and faster

### 3. **Scalability**
- **Parallel writes**: Different positions can be written to different partitions in parallel
- **Parallel reads**: Multiple positions can be queried from different partitions simultaneously
- **Horizontal scaling**: Can add more partitions as data grows

### 4. **Maintenance**
- **Partition-level operations**: Can maintain/backup individual partitions
- **Partition pruning**: Old partitions can be archived separately
- **Index maintenance**: Smaller indexes per partition are faster to maintain

## Indexes on Partitioned Table

Indexes are created on the partitioned table and automatically propagate to all partitions:

```sql
-- Indexes for efficient queries
CREATE INDEX idx_event_replay ON event_store (position_key, event_ver ASC);
CREATE INDEX idx_contract_link ON event_store (contract_id);
CREATE INDEX idx_correlation_id ON event_store (correlation_id);
CREATE INDEX idx_effective_date ON event_store (position_key, effective_date);
```

**Key Index**: `idx_event_replay`
- Used for event stream replay (coldpath)
- Covers queries: `WHERE position_key = ? ORDER BY event_ver ASC`
- Partition pruning ensures only one partition is scanned

## Query Patterns and Partition Pruning

### Pattern 1: Position Event Stream (Most Common)
```sql
SELECT * FROM event_store 
WHERE position_key = 'ABC123' 
ORDER BY event_ver ASC;
```
**Partition Pruning**: ✅ Only scans partition containing `ABC123`

### Pattern 2: Version Range Query
```sql
SELECT * FROM event_store 
WHERE position_key = 'ABC123' 
  AND event_ver BETWEEN 10 AND 20
ORDER BY event_ver ASC;
```
**Partition Pruning**: ✅ Only scans one partition

### Pattern 3: Effective Date Range
```sql
SELECT * FROM event_store 
WHERE position_key = 'ABC123' 
  AND effective_date BETWEEN '2024-01-01' AND '2024-01-31'
ORDER BY effective_date, event_ver;
```
**Partition Pruning**: ✅ Only scans one partition

### Pattern 4: Cross-Position Query (Less Common)
```sql
SELECT * FROM event_store 
WHERE correlation_id = 'CORR-123'
ORDER BY occurred_at;
```
**Partition Pruning**: ❌ Must scan all partitions (uses `idx_correlation_id`)

## Configuration

Partitioning configuration in `application.yml`:

```yaml
app:
  partitioning:
    partition-count: 16
    hash-modulus: 16
```

**Note**: Currently, this configuration is not actively used in the code. PostgreSQL handles partitioning automatically based on the table definition. This configuration could be used for:
- Dynamic partition creation
- Partition monitoring
- Partition routing logic (if needed in application layer)

## Partition Monitoring

### Check Partition Sizes
```sql
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE tablename LIKE 'event_store_p%'
ORDER BY tablename;
```

### Check Row Counts per Partition
```sql
SELECT 
    'event_store_p0' as partition, COUNT(*) as row_count FROM event_store_p0
UNION ALL
SELECT 'event_store_p1', COUNT(*) FROM event_store_p1
-- ... repeat for all partitions
ORDER BY partition;
```

### Check Partition Distribution
```sql
-- See which partition a position key maps to
SELECT 
    position_key,
    (hashtext(position_key) % 16) as partition_number
FROM event_store
GROUP BY position_key
LIMIT 100;
```

## Performance Considerations

### 1. **Partition Count Selection**
- **16 partitions**: Good balance for current scale (2M trades/day)
- **Too few partitions**: Less parallelism, larger partitions
- **Too many partitions**: Overhead, more partition management

### 2. **Partition Size**
- Each partition should ideally stay under 10-50GB for optimal performance
- Monitor partition sizes and consider adding partitions if needed

### 3. **Hot Spots**
- If a single position has extremely high volume, it can create a hot spot
- Monitor partition-level metrics for uneven distribution
- Consider sub-partitioning by date if needed

### 4. **Query Performance**
- **Partition pruning** is automatic for position-key queries
- **Cross-partition queries** (e.g., by correlation_id) scan all partitions
- Use appropriate indexes for cross-partition queries

## Future Enhancements

### 1. **Sub-Partitioning by Date**
If partition sizes grow too large, consider sub-partitioning by date:
```sql
-- Example: Range sub-partitioning by month
CREATE TABLE event_store_p0_2024_01 PARTITION OF event_store_p0
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

### 2. **Dynamic Partition Management**
- Automatically create new partitions as data grows
- Archive old partitions to cold storage
- Implement partition lifecycle management

### 3. **Partition-Aware Routing**
- Application-level routing to specific database connections
- Connection pool per partition for better isolation
- Partition-specific read replicas

### 4. **Monitoring and Alerting**
- Partition size monitoring
- Query performance per partition
- Hot spot detection
- Partition-level metrics (writes, reads, size)

## Comparison with Other Partitioning Strategies

### Hash Partitioning (Current)
✅ **Pros**:
- Even distribution
- Predictable partition assignment
- Good for random access patterns

❌ **Cons**:
- Cannot easily archive old data (no date-based pruning)
- All partitions must be queried for date-range queries across positions

### Range Partitioning (Alternative)
✅ **Pros**:
- Easy to archive old data
- Date-based partition pruning
- Natural for time-series data

❌ **Cons**:
- Potential hot spots (recent partitions)
- Uneven distribution if data is time-skewed
- More complex partition management

### List Partitioning (Alternative)
✅ **Pros**:
- Explicit control over partition assignment
- Good for known, discrete values

❌ **Cons**:
- Not suitable for continuous values like position keys
- Manual partition management

## Best Practices

1. **Monitor Partition Sizes**: Keep partitions under 50GB for optimal performance
2. **Even Distribution**: Monitor hash distribution to ensure even spread
3. **Index Maintenance**: Regularly analyze and rebuild indexes per partition
4. **Query Patterns**: Design queries to leverage partition pruning
5. **Backup Strategy**: Consider partition-level backups for large datasets
6. **Archival Strategy**: Plan for archiving old partitions to cold storage

## Summary

The current partitioning design uses **hash partitioning by position_key** with **16 partitions**, providing:
- ✅ Sequential ordering guarantee for position events
- ✅ Efficient position-specific queries (partition pruning)
- ✅ Parallel processing capabilities
- ✅ Scalability for high-throughput scenarios
- ✅ Simple, predictable partition assignment

This design is optimal for the event sourcing pattern where position-specific event streams are the primary access pattern.
