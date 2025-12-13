# Event Store Debugging Guide

## Issue: No Data in Event Store

If you're not seeing data in the event store, follow these diagnostic steps:

## Diagnostic Steps

### 1. Check Database Connection

```bash
# Using the check script
./scripts/check-event-store.sh

# Or manually
psql -h localhost -U postgres -d equity_swap_db -c "SELECT COUNT(*) FROM event_store;"
```

### 2. Check Application Logs

Look for these log messages:
- `✅ Saved event for position...` - Event was saved
- `✅ FINAL VERIFICATION: Event confirmed in database` - Event verified after save
- `❌ CRITICAL: Event was saved but cannot be retrieved` - Transaction may have rolled back
- `❌ Error saving snapshot` - Snapshot save failed (may cause rollback)

### 3. Use Diagnostic Endpoints

Once the application is running:

```bash
# Get total event count
curl http://localhost:8080/api/diagnostics/events/count

# Get events for a specific position
curl http://localhost:8080/api/diagnostics/events/position/{positionKey}

# Get specific event
curl http://localhost:8080/api/diagnostics/events/position/{positionKey}/version/{version}

# Get latest events
curl http://localhost:8080/api/diagnostics/events/latest/10
```

### 4. Check Transaction Status

The `processCurrentDatedTrade` method is `@Transactional`, which means:
- **Transaction commits** when the method completes successfully
- **Transaction rolls back** if an exception is thrown

**Common causes of rollback:**
1. Exception in `updateSnapshot()` - Now caught, won't rollback
2. Exception in `snapshotRepository.saveAndFlush()` - Now caught, won't rollback
3. Exception in `idempotencyService.markAsProcessed()` - Now caught, won't rollback
4. Database constraint violation
5. Connection issues

### 5. Verify Event Save

The code now includes:
- Immediate flush after event save
- Final verification query after all operations
- Error handling to prevent rollback if later operations fail

## Code Changes Made

### 1. Error Handling
- Wrapped snapshot save in try-catch to prevent rollback
- Wrapped idempotency marking in try-catch to prevent rollback
- Event save is now isolated from later failures

### 2. Verification
- Added final verification query after all operations
- Logs confirmation if event is found in database
- Logs error if event is not found (indicates rollback)

### 3. Transaction Configuration
- Explicit `rollbackFor = Exception.class` to ensure proper rollback behavior
- Flush operations to ensure data is written

## Common Issues and Solutions

### Issue 1: Transaction Not Committing

**Symptoms:**
- Logs show "Saved event" but query returns no data
- Final verification fails

**Possible Causes:**
- Exception after event save causing rollback
- Transaction isolation level
- Database connection issues

**Solution:**
- Check logs for exceptions after event save
- Verify transaction is completing successfully
- Check database connection

### Issue 2: Wrong Database/Connection

**Symptoms:**
- Application connects but data not visible
- Different database than expected

**Solution:**
- Verify `application.yml` database configuration
- Check which database the application is connecting to
- Verify database name: `equity_swap_db`

### Issue 3: Partitioned Table Issue

**Symptoms:**
- Events saved but not visible in queries
- Wrong partition

**Solution:**
- Check all partitions:
  ```sql
  SELECT schemaname, tablename, n_live_tup 
  FROM pg_stat_user_tables 
  WHERE tablename LIKE 'event_store%';
  ```

### Issue 4: Transaction Isolation

**Symptoms:**
- Data visible in one connection but not another
- Uncommitted transaction

**Solution:**
- Check transaction isolation level
- Ensure transactions are committing
- Use `COMMIT` explicitly if needed

## Testing

### Run E2E Test and Check Database

```bash
# 1. Run test
mvn test -pl api -Dtest='EndToEndIntegrationTest#testEndToEndFlow_NewTrade' -Dspring.profiles.active=e2e

# 2. Check event store (if using Testcontainers, events are in test DB)
# For production, use:
./scripts/check-event-store.sh
```

### Manual Verification

```sql
-- Connect to database
psql -h localhost -U postgres -d equity_swap_db

-- Check total events
SELECT COUNT(*) FROM event_store;

-- Check latest events
SELECT position_key, event_ver, event_type, effective_date, occurred_at 
FROM event_store 
ORDER BY occurred_at DESC 
LIMIT 10;

-- Check by position
SELECT * FROM event_store 
WHERE position_key = 'your-position-key' 
ORDER BY event_ver;
```

## Next Steps

1. **Run the diagnostic script**: `./scripts/check-event-store.sh`
2. **Check application logs** for verification messages
3. **Use diagnostic endpoints** to query event store
4. **Verify database connection** and configuration
5. **Check for exceptions** that might cause rollback

## Expected Behavior

After processing a trade:
1. Event is saved to `event_store` table
2. Snapshot is updated in `snapshot_store` table
3. Idempotency record is created in `idempotency_store` table
4. Transaction commits
5. All data is visible in database

If any step fails, check logs for the specific error.
