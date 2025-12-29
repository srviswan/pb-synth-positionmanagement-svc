# MS SQL Server Support Implementation

## Overview

The Position Management Service now supports both **PostgreSQL** (default) and **MS SQL Server** databases. The database type can be configured via environment variables or `application.yml`.

## Implementation Summary

### 1. Dependencies Added

**`infrastructure/pom.xml`**:
- Added `mssql-jdbc` dependency
- Added `flyway-database-sqlserver` dependency

### 2. Configuration Classes

**`DatabaseConfig.java`**:
- Auto-configures DataSource based on `DB_TYPE` environment variable
- Defaults to PostgreSQL if not specified
- Constructs JDBC URL if not provided

**`JpaConfig.java`**:
- Sets Hibernate dialect based on database type
- PostgreSQL: `org.hibernate.dialect.PostgreSQLDialect`
- MS SQL Server: `org.hibernate.dialect.SQLServerDialect`

**`FlywayConfig.java`**:
- Selects migration directory based on database type
- PostgreSQL: `classpath:db/migration`
- MS SQL Server: `classpath:db/migration/sqlserver`

### 3. Database-Agnostic Entities

All entity annotations updated to remove hardcoded `columnDefinition`:
- `@JdbcTypeCode(SqlTypes.JSON)` without `columnDefinition="jsonb"`
- Hibernate automatically maps to:
  - PostgreSQL: JSONB
  - MS SQL Server: NVARCHAR(MAX)

**Updated Entities**:
- `SnapshotEntity`
- `EventEntity`
- `ReconciliationBreakEntity`
- `RegulatorySubmissionEntity`

### 4. Migration Files

**PostgreSQL Migrations**: `infrastructure/src/main/resources/db/migration/`
- V1__initial_schema.sql (with hash partitioning)
- V2__add_price_quantity_schedule.sql
- V3__create_upi_history_table.sql
- V4__add_archival_flag.sql
- V5__add_lookup_fields_to_snapshot.sql

**MS SQL Server Migrations**: `infrastructure/src/main/resources/db/migration/sqlserver/`
- V1__initial_schema.sql (non-partitioned, with all tables)
- V2__add_price_quantity_schedule.sql (placeholder - already in V1)
- V3__create_upi_history_table.sql (placeholder - already in V1)
- V4__add_archival_flag.sql (placeholder - already in V1)
- V5__add_lookup_fields_to_snapshot.sql (placeholder - already in V1)

### 5. Key Differences Handled

| Feature | PostgreSQL | MS SQL Server |
|---------|-----------|---------------|
| JSON Storage | JSONB | NVARCHAR(MAX) |
| Timestamp | TIMESTAMPTZ | DATETIMEOFFSET |
| UUID | `uuid_generate_v4()` | `NEWID()` |
| Partitioning | Hash partitioning | Standard indexes |
| Filtered Indexes | Supported (WHERE clause) | Standard indexes only |
| Boolean | BOOLEAN | BIT |

### 6. Test Configuration

**`SqlServerIntegrationTest.java`**:
- Uses `MSSQLServerContainer` from Testcontainers
- Tests position creation and lifecycle
- Verifies lookup fields are populated correctly

## Usage

### Running with PostgreSQL (Default)

```bash
# No configuration needed
mvn spring-boot:run -pl api
```

### Running with MS SQL Server

```bash
export DB_TYPE=sqlserver
export DB_HOST=localhost
export DB_PORT=1433
export DB_NAME=equity_swap_db
export DB_USERNAME=SA
export DB_PASSWORD=YourPassword

mvn spring-boot:run -pl api
```

Or provide full JDBC URL:

```bash
export DB_TYPE=sqlserver
export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=equity_swap_db;encrypt=true;trustServerCertificate=true"
export DB_USERNAME=SA
export DB_PASSWORD=YourPassword

mvn spring-boot:run -pl api
```

### Testing

**PostgreSQL Test**:
```bash
mvn test -pl api -Dtest='EndToEndIntegrationTest'
```

**MS SQL Server Test**:
```bash
mvn test -pl api -Dtest='SqlServerIntegrationTest'
```

## Configuration Properties

All database configuration is done via environment variables or `application.yml`:

- `DB_TYPE`: `postgresql` (default) or `sqlserver`
- `DB_URL`: Optional full JDBC URL
- `DB_HOST`: Database host (default: localhost)
- `DB_PORT`: Database port (5432 for PostgreSQL, 1433 for SQL Server)
- `DB_NAME`: Database name (default: equity_swap_db)
- `DB_USERNAME`: Database username
- `DB_PASSWORD`: Database password

## Migration Strategy

Flyway automatically:
1. Detects database type from `DB_TYPE`
2. Selects appropriate migration directory
3. Runs migrations in version order
4. Validates schema on startup

## Notes

- PostgreSQL code is **not removed** - both databases are fully supported
- Switching databases only requires changing `DB_TYPE` environment variable
- All business logic remains the same - only database-specific SQL differs
- JSON handling is transparent - Hibernate handles the mapping
- Partitioning strategy differs (PostgreSQL uses hash partitioning, SQL Server uses indexes)

## Testing Status

✅ Configuration classes created
✅ Migration files created for MS SQL Server
✅ Entities made database-agnostic
✅ Test class created (`SqlServerIntegrationTest`)
⏳ Full test execution pending (requires Docker/Testcontainers)

## Next Steps

1. Run `SqlServerIntegrationTest` to verify MS SQL Server functionality
2. Test with real MS SQL Server instance if needed
3. Verify all endpoints work with MS SQL Server
4. Performance test with MS SQL Server
