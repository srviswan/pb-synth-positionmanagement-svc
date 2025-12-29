# MS SQL Server Support - Implementation Summary

## ✅ Implementation Complete

MS SQL Server support has been successfully added to the Position Management Service alongside PostgreSQL. The system is now **database-agnostic** and can switch between databases via configuration.

## What Was Implemented

### 1. Dependencies ✅
- Added `mssql-jdbc` dependency
- Added `flyway-database-sqlserver` dependency

### 2. Configuration Classes ✅
- **`DatabaseConfig.java`**: Auto-configures DataSource based on `DB_TYPE`
- **`JpaConfig.java`**: Sets Hibernate dialect automatically
- **`FlywayConfig.java`**: Selects migration directory based on database type

### 3. Database-Agnostic Entities ✅
- Removed hardcoded `columnDefinition="jsonb"` from all JSON columns
- Hibernate automatically maps `@JdbcTypeCode(SqlTypes.JSON)` to:
  - PostgreSQL: JSONB
  - MS SQL Server: NVARCHAR(MAX)

### 4. MS SQL Server Migrations ✅
Created complete migration set in `infrastructure/src/main/resources/db/migration/sqlserver/`:
- V1__initial_schema.sql (all tables with MS SQL Server syntax)
- V2__add_price_quantity_schedule.sql (placeholder)
- V3__create_upi_history_table.sql (placeholder - already in V1)
- V4__add_archival_flag.sql (placeholder - already in V1)
- V5__add_lookup_fields_to_snapshot.sql (placeholder - already in V1)

### 5. Test Suite ✅
- Created `SqlServerIntegrationTest.java` with Testcontainers
- Tests position creation and lifecycle
- Verifies lookup fields are populated

### 6. Documentation ✅
- `DATABASE_CONFIGURATION.md`: Configuration guide
- `MS_SQL_SERVER_SUPPORT.md`: Implementation details
- `test-sqlserver.sh`: Test script

## Key Differences Handled

| Aspect | PostgreSQL | MS SQL Server |
|--------|-----------|---------------|
| JSON | JSONB | NVARCHAR(MAX) |
| Timestamp | TIMESTAMPTZ | DATETIMEOFFSET |
| UUID | `uuid_generate_v4()` | `NEWID()` |
| Boolean | BOOLEAN | BIT |
| Partitioning | Hash partitioning | Standard indexes |
| Filtered Indexes | Supported | Standard indexes |

## How to Use

### Switch to MS SQL Server

```bash
export DB_TYPE=sqlserver
export DB_HOST=localhost
export DB_PORT=1433
export DB_NAME=equity_swap_db
export DB_USERNAME=SA
export DB_PASSWORD=YourPassword

mvn spring-boot:run -pl api
```

### Switch Back to PostgreSQL

```bash
export DB_TYPE=postgresql
# Or simply unset DB_TYPE (defaults to postgresql)

mvn spring-boot:run -pl api
```

## Testing

### Run MS SQL Server Tests

```bash
./scripts/test-sqlserver.sh
```

Or directly:
```bash
mvn test -pl api -Dtest='SqlServerIntegrationTest'
```

## Files Modified

### New Files
- `infrastructure/src/main/java/com/bank/esps/infrastructure/config/DatabaseConfig.java`
- `infrastructure/src/main/java/com/bank/esps/infrastructure/config/JpaConfig.java`
- `infrastructure/src/main/java/com/bank/esps/infrastructure/config/FlywayConfig.java`
- `infrastructure/src/main/resources/db/migration/sqlserver/V1__initial_schema.sql`
- `infrastructure/src/main/resources/db/migration/sqlserver/V2-V5__*.sql`
- `api/src/test/java/com/bank/esps/integration/SqlServerIntegrationTest.java`
- `scripts/test-sqlserver.sh`
- `documents/DATABASE_CONFIGURATION.md`
- `documents/MS_SQL_SERVER_SUPPORT.md`

### Modified Files
- `infrastructure/pom.xml` (added MS SQL Server dependencies)
- `api/src/main/resources/application.yml` (database configuration)
- All entity classes (removed hardcoded columnDefinition)

## Status

✅ **Implementation Complete**
⏳ **Testing Pending** (requires Docker/Testcontainers to run)

All code changes are complete and ready for testing. The system will automatically:
1. Detect database type from `DB_TYPE`
2. Configure DataSource appropriately
3. Select correct Hibernate dialect
4. Run appropriate Flyway migrations
5. Map JSON columns correctly
