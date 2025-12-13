# Event Store Debugging Guide

## Issue: No Data in Event Store

If you're not seeing data in the event store, here are steps to diagnose and fix:

## Diagnostic Steps

### 1. Check if Events are Being Saved

**Added Logging:**
- Events now log: `✅ Saved event for position {}: version {}, type {}`
- Events are verified after save to confirm they exist in database
- Check application logs for these messages

**Use Diagnostic Endpoint:**
```bash
# Get total event count
curl http://localhost:8080/api/diagnostics/events/count

# Get events for a specific position
curl http://localhost:8080/api/diagnostics/events/position/{positionKey}

# Get latest version for a position
curl http://localhost:8080/api/diagnostics/events/position/{positionKey}/latest-version
```

### 2. Check Database Connection

```bash
# Connect to PostgreSQL
psql -h localhost -U postgres -d equity_swap_db

# Check if event_store table exists
\dt event_store*

# Check event count
SELECT COUNT(*) FROM event_store;

# Check events for a position
SELECT * FROM event_store WHERE position_key = 'your-position-key' ORDER BY event_ver;

# Check all partitions
SELECT 
    schemaname,
    tablename,
    n_live_tup as row_count
FROM pg_stat_user_tables
WHERE tablename LIKE 'event_store%'
ORDER BY tablename;
```

### 3. Check Transaction Issues

**Potential Issues:**
- Events saved but transaction rolled back
- Events saved but not committed
- Events saved to wrong partition

**Fixes Applied:**
1. ✅ Added `entityManager.flush()` after saving events
2. ✅ Added verification query after save
3. ✅ Added better error logging
4. ✅ Events are saved before snapshot update

### 4. Check Partition Routing

The event store is partitioned by hash of `position_key`. Events might be in different partitions:

```sql
-- Check which partition a position key maps to
SELECT 
    position_key,
    hashtext(position_key) % 16 as partition_number,
    event_ver,
    event_type
FROM event_store
WHERE position_key = 'your-position-key'
ORDER BY event_ver;
```

### 5. Verify Application is Processing Trades

**Check Logs:**
```bash
# Look for trade processing logs
grep "processCurrentDatedTrade" logs/position-service.log

# Look for event save logs
grep "Saved event" logs/position-service.log

# Look for errors
grep "ERROR\|Exception" logs/position-service.log
```

### 6. Test with E2E Test

The E2E tests verify events are saved:

```bash
mvn test -pl api -Dtest=EndToEndIntegrationTest#testEndToEndFlow_NewTrade -Dspring.profiles.active=e2e
```

After test, check Testcontainers database:
```java
// In test, after processing trade
List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
assertTrue(events.size() > 0, "Events should be saved");
```

## Common Issues and Fixes

### Issue 1: Events Saved but Not Visible

**Cause**: Transaction not committed  
**Fix**: Added `entityManager.flush()` after save

### Issue 2: Events in Wrong Partition

**Cause**: Partition routing issue  
**Fix**: Query all partitions or use diagnostic endpoint

### Issue 3: Events Rolled Back

**Cause**: Exception after event save but before commit  
**Fix**: Check logs for exceptions, ensure snapshot save succeeds

### Issue 4: Database Connection Issue

**Cause**: Wrong database or connection failure  
**Fix**: Verify `application.yml` database configuration

## Verification Queries

### Count Events by Type
```sql
SELECT event_type, COUNT(*) 
FROM event_store 
GROUP BY event_type;
```

### Latest Events
```sql
SELECT position_key, MAX(event_ver) as latest_version
FROM event_store
GROUP BY position_key
ORDER BY latest_version DESC
LIMIT 10;
```

### Events by Date
```sql
SELECT 
    DATE(occurred_at) as date,
    COUNT(*) as event_count
FROM event_store
GROUP BY DATE(occurred_at)
ORDER BY date DESC;
```

## Debugging Checklist

- [ ] Check application logs for "Saved event" messages
- [ ] Verify database connection is working
- [ ] Check if events table exists and has correct schema
- [ ] Query event_store table directly
- [ ] Check all partitions (event_store_p0 through event_store_p15)
- [ ] Verify transaction is committing (check snapshot_store for data)
- [ ] Use diagnostic endpoint to query events
- [ ] Run E2E test and verify events are saved
- [ ] Check for exceptions in logs that might cause rollback

## Next Steps

If events are still not appearing:

1. **Enable SQL Logging**:
   ```yaml
   logging:
     level:
       org.hibernate.SQL: DEBUG
       org.hibernate.type.descriptor.sql.BasicBinder: TRACE
   ```

2. **Check Hibernate Statistics**:
   ```yaml
   spring:
     jpa:
       properties:
         hibernate:
           generate_statistics: true
   ```

3. **Add Database Trigger**:
   Create a trigger to log all inserts to event_store

4. **Use Database Monitoring**:
   Enable PostgreSQL logging to see all INSERT statements
