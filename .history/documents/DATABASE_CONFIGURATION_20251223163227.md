# Database Configuration Guide

The Position Management Service supports both **PostgreSQL** and **MS SQL Server** databases. The database type can be configured via environment variables or `application.yml`.

## Configuration

### Environment Variables

Set the following environment variables to configure the database:

```bash
# Database Type: postgresql (default) or sqlserver
export DB_TYPE=sqlserver

# Database Connection (optional - defaults provided)
export DB_HOST=localhost
export DB_PORT=1433  # 5432 for PostgreSQL, 1433 for MS SQL Server
export DB_NAME=equity_swap_db
export DB_USERNAME=SA  # postgres for PostgreSQL, SA for SQL Server
export DB_PASSWORD=YourPassword

# Or provide full JDBC URL
export DB_URL=jdbc:sqlserver://localhost:1433;databaseName=equity_swap_db;encrypt=true;trustServerCertificate=true
```

### Application Configuration

In `application.yml`:

```yaml
spring:
  datasource:
    type: ${DB_TYPE:postgresql}  # postgresql or sqlserver
    url: ${DB_URL:}  # Optional: full JDBC URL
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: ${DB_DRIVER:}  # Auto-detected based on DB_TYPE
```

## Database Differences

### PostgreSQL (Default)

- **JSON Storage**: JSONB (binary JSON with indexing support)
- **Timestamp**: TIMESTAMPTZ (timestamp with timezone)
- **UUID Generation**: `uuid_generate_v4()` or `gen_random_uuid()`
- **Partitioning**: Hash partitioning supported
- **Indexes**: Filtered indexes with WHERE clauses supported
- **Migrations**: `classpath:db/migration`

### MS SQL Server

- **JSON Storage**: NVARCHAR(MAX) (Hibernate maps @JdbcTypeCode(SqlTypes.JSON) automatically)
- **Timestamp**: DATETIMEOFFSET (equivalent to TIMESTAMPTZ)
- **UUID Generation**: NEWID() or UNIQUEIDENTIFIER with DEFAULT NEWID()
- **Partitioning**: Different partitioning strategy (not hash-based like PostgreSQL)
- **Indexes**: Standard indexes (filtered indexes not supported in same way)
- **Migrations**: `classpath:db/migration/sqlserver`

## Migration Files

Migrations are stored in separate directories:

- **PostgreSQL**: `infrastructure/src/main/resources/db/migration/`
- **MS SQL Server**: `infrastructure/src/main/resources/db/migration/sqlserver/`

Flyway automatically selects the correct migration directory based on `DB_TYPE`.

## Entity Annotations

Entities use database-agnostic annotations:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "payload", nullable = false)
private String payload; // JSONB in PostgreSQL, NVARCHAR(MAX) in SQL Server
```

Hibernate automatically maps `@JdbcTypeCode(SqlTypes.JSON)` to:
- **PostgreSQL**: JSONB
- **MS SQL Server**: NVARCHAR(MAX)

## Testing

### PostgreSQL Test

```bash
# Uses default PostgreSQL Testcontainers
mvn test -pl api -Dtest='EndToEndIntegrationTest'
```

### MS SQL Server Test

```bash
# Uses MS SQL Server Testcontainers
mvn test -pl api -Dtest='SqlServerIntegrationTest'
```

## Running with Different Databases

### PostgreSQL (Default)

```bash
# No configuration needed - uses PostgreSQL by default
mvn spring-boot:run -pl api
```

### MS SQL Server

```bash
# Set environment variable
export DB_TYPE=sqlserver
export DB_HOST=localhost
export DB_PORT=1433
export DB_NAME=equity_swap_db
export DB_USERNAME=SA
export DB_PASSWORD=YourPassword

mvn spring-boot:run -pl api
```

Or use Docker Compose:

```yaml
# docker-compose-sqlserver.yml
services:
  sqlserver:
    image: mcr.microsoft.com/mssql/server:2022-latest
    environment:
      ACCEPT_EULA: Y
      SA_PASSWORD: Test@123456
    ports:
      - "1433:1433"
```

## Key Implementation Details

1. **DatabaseConfig**: Auto-configures DataSource based on `DB_TYPE`
2. **JpaConfig**: Sets Hibernate dialect based on `DB_TYPE`
3. **FlywayConfig**: Selects migration directory based on `DB_TYPE`
4. **Entities**: Use `@JdbcTypeCode(SqlTypes.JSON)` without hardcoded `columnDefinition`
5. **Migrations**: Separate SQL files for each database type

## Notes

- PostgreSQL hash partitioning is not replicated in SQL Server (uses standard indexes instead)
- JSONB-specific functions in PostgreSQL are replaced with JSON functions in SQL Server
- Filtered indexes (WHERE clauses) in PostgreSQL are replaced with standard indexes in SQL Server
- Both databases support the same functionality, with minor syntax differences handled in migrations
