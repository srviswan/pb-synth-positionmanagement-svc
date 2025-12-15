# Event Store Archival Strategy

## Overview

The event store uses **partition-level archival** to efficiently move old data to archival storage while keeping active data in the main database. This strategy leverages PostgreSQL's hash partitioning to archive entire partitions at once.

## Architecture

### Partition-Level Archival

Instead of archiving individual rows, we archive **entire partitions** based on:
- **Age**: Events older than a retention period (e.g., 7 years)
- **Status**: Positions that are TERMINATED and no longer active
- **Business Rules**: Regulatory retention requirements

### Benefits

1. **Efficiency**: Move entire partitions in a single operation
2. **Performance**: No impact on active partition queries
3. **Cost**: Move to cheaper archival storage (S3, Glacier, etc.)
4. **Compliance**: Maintain audit trail while reducing storage costs

## Schema Design

### Archival Columns

```sql
archival_flag BOOLEAN NOT NULL DEFAULT FALSE
archived_at TIMESTAMPTZ
```

- **`archival_flag`**: Marks events as archived (default: `FALSE` for active data)
- **`archived_at`**: Timestamp when the partition was archived

### Indexes

1. **Partial Index on Active Data**:
   ```sql
   CREATE INDEX idx_event_store_archival_flag ON event_store (archival_flag) 
   WHERE archival_flag = FALSE;
   ```
   - Optimizes queries for active data only
   - Smaller index size

2. **Index on Archived Timestamp**:
   ```sql
   CREATE INDEX idx_event_store_archived_at ON event_store (archived_at) 
   WHERE archived_at IS NOT NULL;
   ```
   - Supports archival queries and reporting

3. **Composite Index for Archival Queries**:
   ```sql
   CREATE INDEX idx_event_store_archival_partition ON event_store (archival_flag, position_key, effective_date)
   WHERE archival_flag = TRUE;
   ```
   - Optimizes queries for archived data

## Archival Process

### Step 1: Identify Partitions for Archival

```sql
-- Find partitions with old data (e.g., events older than 7 years)
SELECT 
    'event_store_p' || (hashtext(position_key) % 16) as partition_name,
    COUNT(*) as event_count,
    MIN(effective_date) as oldest_date,
    MAX(effective_date) as newest_date
FROM event_store
WHERE archival_flag = FALSE
  AND effective_date < CURRENT_DATE - INTERVAL '7 years'
GROUP BY hashtext(position_key) % 16
HAVING MIN(effective_date) < CURRENT_DATE - INTERVAL '7 years'
ORDER BY oldest_date;
```

### Step 2: Mark Events for Archival

```sql
-- Mark all events in a partition as archived
UPDATE event_store
SET 
    archival_flag = TRUE,
    archived_at = CURRENT_TIMESTAMP
WHERE hashtext(position_key) % 16 = 0  -- Partition 0
  AND effective_date < CURRENT_DATE - INTERVAL '7 years'
  AND archival_flag = FALSE;
```

### Step 3: Export Partition Data

```sql
-- Export archived partition to CSV/Parquet for archival storage
COPY (
    SELECT * FROM event_store
    WHERE hashtext(position_key) % 16 = 0
      AND archival_flag = TRUE
) TO '/archive/event_store_p0_2024.csv' WITH CSV HEADER;
```

### Step 4: Move Partition to Archival Storage

Options:
1. **AWS S3/Glacier**: Upload exported files
2. **Azure Blob Storage**: Archive tier
3. **Google Cloud Storage**: Coldline/Nearline
4. **Dedicated Archival Database**: Separate PostgreSQL instance

### Step 5: Verify and Clean Up

```sql
-- Verify archival is complete
SELECT 
    COUNT(*) as archived_count,
    MIN(archived_at) as first_archived,
    MAX(archived_at) as last_archived
FROM event_store
WHERE hashtext(position_key) % 16 = 0
  AND archival_flag = TRUE;

-- Optional: Delete archived data from main database (after verification)
-- WARNING: Only do this after confirming data is safely archived!
-- DELETE FROM event_store
-- WHERE hashtext(position_key) % 16 = 0
--   AND archival_flag = TRUE;
```

## Query Patterns

### Active Data Only (Default)

```sql
-- Application queries automatically exclude archived data
SELECT * FROM event_store
WHERE position_key = 'a1b2c3d4...'
  AND archival_flag = FALSE  -- Implicit in application queries
ORDER BY event_ver;
```

### Include Archived Data (Historical Queries)

```sql
-- For historical analysis or compliance queries
SELECT * FROM event_store
WHERE position_key = 'a1b2c3d4...'
  AND (archival_flag = FALSE OR archival_flag = TRUE)  -- Include all
ORDER BY event_ver;
```

### Archive-Specific Queries

```sql
-- Find all archived events for a position
SELECT * FROM event_store
WHERE position_key = 'a1b2c3d4...'
  AND archival_flag = TRUE
ORDER BY archived_at DESC;
```

## Application Integration

### EventEntity Updates

The `EventEntity` class includes archival fields:

```java
@Column(name = "archival_flag", nullable = false)
@Builder.Default
private Boolean archivalFlag = false;

@Column(name = "archived_at")
private OffsetDateTime archivedAt;
```

### Repository Queries

Update repository methods to filter archived data:

```java
// Default: Only active data
@Query("SELECT e FROM EventEntity e WHERE e.positionKey = :positionKey " +
       "AND e.archivalFlag = false ORDER BY e.eventVer")
List<EventEntity> findByPositionKeyOrderByEventVer(@Param("positionKey") String positionKey);

// Include archived: For historical queries
@Query("SELECT e FROM EventEntity e WHERE e.positionKey = :positionKey " +
       "ORDER BY e.eventVer")
List<EventEntity> findByPositionKeyOrderByEventVerIncludingArchived(@Param("positionKey") String positionKey);
```

### Service Layer

```java
public List<EventEntity> loadEventStream(String positionKey, boolean includeArchived) {
    if (includeArchived) {
        return eventStoreRepository.findByPositionKeyOrderByEventVerIncludingArchived(positionKey);
    } else {
        return eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
    }
}
```

## Archival Service Implementation

### ArchivalService

```java
@Service
public class ArchivalService {
    
    @Autowired
    private EventStoreRepository eventStoreRepository;
    
    /**
     * Mark partition for archival based on age
     */
    @Transactional
    public int markPartitionForArchival(int partitionNumber, LocalDate cutoffDate) {
        // Calculate partition hash range
        int remainder = partitionNumber;
        
        // Mark events in partition as archived
        return eventStoreRepository.markPartitionForArchival(remainder, cutoffDate);
    }
    
    /**
     * Export archived partition to file
     */
    public void exportPartitionToFile(int partitionNumber, String outputPath) {
        List<EventEntity> archivedEvents = eventStoreRepository
            .findArchivedEventsByPartition(partitionNumber);
        
        // Export to CSV/Parquet
        // Implementation depends on archival storage choice
    }
    
    /**
     * Verify archival completeness
     */
    public ArchivalStatus verifyArchival(int partitionNumber) {
        // Check that all events in partition are archived
        // Verify exported file integrity
        // Return status
    }
}
```

## Retention Policies

### Regulatory Requirements

- **Trade Data**: 7 years (typical regulatory requirement)
- **Position History**: 10 years (for audit purposes)
- **UPI History**: Permanent (for compliance)

### Archival Schedule

1. **Daily**: Check for partitions ready for archival
2. **Weekly**: Mark partitions older than retention period
3. **Monthly**: Export and move to archival storage
4. **Quarterly**: Verify archival completeness

## Monitoring and Alerts

### Metrics

- **Archived Event Count**: Total events archived
- **Archival Lag**: Time between cutoff date and archival
- **Archival Success Rate**: Percentage of successful archival operations
- **Storage Savings**: Reduction in main database size

### Alerts

- **Archival Failure**: Alert if archival process fails
- **Data Loss Risk**: Alert if archived data cannot be verified
- **Retention Violation**: Alert if data older than retention period is not archived

## Best Practices

### 1. **Never Delete Without Backup**

Always verify data is safely archived before deleting from main database.

### 2. **Partition-Level Operations**

Archive entire partitions, not individual rows, for efficiency.

### 3. **Verify Before Delete**

Implement verification checksums and integrity checks before deletion.

### 4. **Maintain Audit Trail**

Keep metadata about what was archived, when, and where.

### 5. **Test Restoration**

Periodically test restoring archived data to ensure process works.

### 6. **Documentation**

Maintain clear documentation of:
- What data is archived
- Where it's stored
- How to restore it
- Retention policies

## Restoration Process

### Restore from Archive

```sql
-- 1. Import from archival storage
COPY event_store FROM '/archive/event_store_p0_2024.csv' WITH CSV HEADER;

-- 2. Mark as active (if restoring to main database)
UPDATE event_store
SET 
    archival_flag = FALSE,
    archived_at = NULL
WHERE hashtext(position_key) % 16 = 0
  AND archival_flag = TRUE;
```

## Cost Optimization

### Storage Tiers

1. **Hot Storage** (Main DB): Active data (< 1 year)
2. **Warm Storage** (S3 Standard): Recent archival (1-3 years)
3. **Cold Storage** (S3 Glacier): Old archival (3-7 years)
4. **Deep Archive** (Glacier Deep Archive): Very old (7+ years)

### Estimated Savings

For 2M trades/day over 7 years:
- **Active Data**: ~5.1B events (1 year) = ~500GB
- **Archived Data**: ~35.7B events (7 years) = ~3.5TB
- **Savings**: Move 3.5TB from expensive hot storage to cheaper archival = **~70-90% cost reduction**

## Summary

The archival strategy provides:

✅ **Efficient Partition-Level Archival**: Move entire partitions at once  
✅ **Cost Optimization**: Move old data to cheaper storage  
✅ **Compliance**: Maintain audit trail while reducing costs  
✅ **Performance**: No impact on active data queries  
✅ **Flexibility**: Support both active and archived data queries  

The `archival_flag` and `archived_at` columns enable this strategy while maintaining data integrity and query performance.
