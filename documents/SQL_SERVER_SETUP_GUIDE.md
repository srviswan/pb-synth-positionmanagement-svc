# MS SQL Server Setup Guide - Brand New Implementation

This guide provides step-by-step instructions for setting up a **brand new** Position Management Service implementation using MS SQL Server as the database.

## Prerequisites

1. **Java 17+** installed and configured
2. **Maven 3.8+** installed
3. **MS SQL Server 2019+** or **Docker** for running SQL Server container
4. **Git** for cloning the repository (if starting fresh)

## Step 1: Set Up MS SQL Server

### Option A: Using Docker (Recommended for Development)

```bash
# Pull MS SQL Server image
docker pull mcr.microsoft.com/mssql/server:2022-latest

# Run SQL Server container
docker run -e "ACCEPT_EULA=Y" \
  -e "SA_PASSWORD=Test@123456" \
  -p 1433:1433 \
  --name position-sqlserver \
  -d mcr.microsoft.com/mssql/server:2022-latest

# Verify container is running
docker ps | grep position-sqlserver
```

### Option B: Using Local MS SQL Server Instance

1. Install MS SQL Server 2019+ on your machine
2. Enable SQL Server Authentication (Mixed Mode)
3. Create a login with appropriate permissions
4. Note the server name, port (default: 1433), and credentials

## Step 2: Clone or Prepare Project

If starting from scratch, ensure you have the project structure:

```bash
# If cloning from repository
git clone <repository-url>
cd pb-synth-positionmanagement-svc

# Or if using existing project, ensure it's clean
mvn clean
```

## Step 3: Configure Environment Variables

Create a `.env` file or export environment variables:

```bash
# Database Configuration
export DB_TYPE=sqlserver
export DB_HOST=localhost
export DB_PORT=1433
export DB_NAME=equity_swap_db
export DB_USERNAME=SA
export DB_PASSWORD=Test@123456

# Optional: Full JDBC URL (overrides above)
# export DB_URL=jdbc:sqlserver://localhost:1433;databaseName=equity_swap_db;encrypt=true;trustServerCertificate=true

# Infrastructure (if needed)
export REDIS_HOST=localhost
export REDIS_PORT=6379
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Application Configuration
export MESSAGING_TYPE=kafka
export CACHE_TYPE=redis
export CONTRACT_SERVICE_TYPE=mock
```

## Step 4: Verify Database Connection

Test the connection to MS SQL Server:

```bash
# Using sqlcmd (if installed)
sqlcmd -S localhost,1433 -U SA -P "Test@123456" -Q "SELECT @@VERSION"

# Or using Docker exec
docker exec -it position-sqlserver /opt/mssql-tools/bin/sqlcmd \
  -S localhost -U SA -P "Test@123456" \
  -Q "SELECT @@VERSION"
```

## Step 5: Build the Project

Build all modules:

```bash
# Clean and build
mvn clean install

# Verify build succeeds
# You should see:
# - infrastructure module builds successfully
# - application module builds successfully
# - api module builds successfully
```

## Step 6: Start the Application

Start the application with MS SQL Server configuration:

```bash
# Set environment variables (if not already set)
export DB_TYPE=sqlserver
export DB_HOST=localhost
export DB_PORT=1433
export DB_NAME=equity_swap_db
export DB_USERNAME=SA
export DB_PASSWORD=Test@123456

# Start the application
mvn spring-boot:run -pl api
```

### Expected Startup Logs

You should see logs indicating MS SQL Server configuration:

```
🔷 Configuring MS SQL Server database
🔷 MS SQL Server URL: jdbc:sqlserver://localhost:1433;databaseName=equity_swap_db;encrypt=true;trustServerCertificate=true
🔷 Configuring Hibernate for MS SQL Server
🔷 Configuring Flyway for MS SQL Server migrations
```

### Verify Database Schema

After startup, verify the schema was created:

```bash
# Using sqlcmd
sqlcmd -S localhost,1433 -U SA -P "Test@123456" -d equity_swap_db \
  -Q "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE'"

# Expected tables:
# - event_store
# - snapshot_store
# - idempotency_store
# - reconciliation_breaks
# - regulatory_submissions
# - upi_history
```

## Step 7: Verify Application Health

Check the health endpoints:

```bash
# Liveness
curl http://localhost:8080/health/liveness

# Readiness
curl http://localhost:8080/health/readiness

# Detailed health
curl http://localhost:8080/health/detailed
```

## Step 8: Run Integration Tests

Run MS SQL Server integration tests:

```bash
# Run SQL Server tests
./scripts/test-sqlserver.sh

# Or directly with Maven
mvn test -pl api -Dtest='SqlServerIntegrationTest'
```

## Step 9: Test Trade Processing

Submit a test trade:

```bash
curl -X POST http://localhost:8080/api/trades \
  -H "Content-Type: application/json" \
  -d '{
    "tradeId": "TEST-001",
    "account": "ACC001",
    "instrument": "AAPL",
    "currency": "USD",
    "quantity": 100,
    "price": 150.00,
    "effectiveDate": "2024-01-15",
    "tradeType": "NEW_TRADE"
  }'
```

Query the position:

```bash
# Get position key (hash of account+instrument+currency)
# For ACC001+AAPL+USD, position key would be generated

# Query position (replace with actual position key)
curl http://localhost:8080/api/positions/{positionKey}
```

## Step 10: Verify Event Store

Check events were stored:

```bash
# Count events
curl http://localhost:8080/api/diagnostics/events/count

# Get events for position
curl http://localhost:8080/api/diagnostics/events/position/{positionKey}
```

## Troubleshooting

### Issue: "Failed to load driver class com.microsoft.sqlserver.jdbc.SQLServerDriver"

**Solution:**
1. Verify `mssql-jdbc` dependency is in `infrastructure/pom.xml`
2. Ensure dependency is not scoped as `runtime` only
3. Rebuild: `mvn clean install`

### Issue: "Cannot connect to SQL Server"

**Solution:**
1. Verify SQL Server is running: `docker ps` or check SQL Server service
2. Check connection string: Ensure `encrypt=true;trustServerCertificate=true` for Docker
3. Verify credentials: Username and password are correct
4. Check firewall: Port 1433 is not blocked

### Issue: "Flyway migration fails"

**Solution:**
1. Check Flyway logs for specific error
2. Verify migration files exist in `infrastructure/src/main/resources/db/migration/sqlserver/`
3. Check database permissions: User needs CREATE TABLE permissions
4. Verify database exists: `SELECT name FROM sys.databases WHERE name='equity_swap_db'`

### Issue: "Application starts but health checks fail"

**Solution:**
1. Check detailed health: `curl http://localhost:8080/health/detailed`
2. Verify database connectivity
3. Check Kafka/Redis connectivity (if enabled)
4. Review application logs for errors

## Configuration Reference

### Database Configuration in `application.yml`

The application uses `app.database.type` to determine database:

```yaml
app:
  database:
    type: ${DB_TYPE:postgresql}  # Set to 'sqlserver' for MS SQL Server
```

### Environment Variables Priority

1. Environment variables (`DB_TYPE`, `DB_HOST`, etc.)
2. `application.yml` defaults
3. System properties

### JDBC URL Format

For MS SQL Server:
```
jdbc:sqlserver://{host}:{port};databaseName={database};encrypt=true;trustServerCertificate=true
```

## Next Steps

1. **Load Testing**: Run load tests with SQL Server
   ```bash
   ./scripts/load-test.sh
   ```

2. **Performance Testing**: Run performance tests
   ```bash
   ./scripts/performance-test.sh
   ```

3. **End-to-End Testing**: Run full E2E tests
   ```bash
   ./scripts/run-e2e-test.sh
   ```

4. **Production Setup**: 
   - Configure production database credentials
   - Set up connection pooling
   - Configure monitoring and alerting
   - Set up backup and recovery procedures

## Additional Resources

- **Database Configuration**: See `documents/DATABASE_CONFIGURATION.md`
- **MS SQL Server Support**: See `documents/MS_SQL_SERVER_SUPPORT.md`
- **Features**: See `documents/FEATURES.md`
- **Implementation Plan**: See `documents/IMPLEMENTATION_PLAN.md`

## Quick Start Script

Create a `start-sqlserver.sh` script:

```bash
#!/bin/bash

# Set environment variables
export DB_TYPE=sqlserver
export DB_HOST=localhost
export DB_PORT=1433
export DB_NAME=equity_swap_db
export DB_USERNAME=SA
export DB_PASSWORD=Test@123456

# Start SQL Server container (if not running)
docker start position-sqlserver || \
  docker run -e "ACCEPT_EULA=Y" \
    -e "SA_PASSWORD=Test@123456" \
    -p 1433:1433 \
    --name position-sqlserver \
    -d mcr.microsoft.com/mssql/server:2022-latest

# Wait for SQL Server to be ready
echo "Waiting for SQL Server to be ready..."
sleep 10

# Build and start application
mvn clean install
mvn spring-boot:run -pl api
```

Make it executable:
```bash
chmod +x start-sqlserver.sh
./start-sqlserver.sh
```

## Summary

This guide provides a complete setup for a brand new MS SQL Server implementation. The service will:

1. ✅ Automatically detect MS SQL Server from `DB_TYPE` environment variable
2. ✅ Configure Hibernate dialect for MS SQL Server
3. ✅ Run MS SQL Server-specific Flyway migrations
4. ✅ Map JSON columns to `NVARCHAR(MAX)`
5. ✅ Use MS SQL Server-specific data types (DATETIMEOFFSET, UNIQUEIDENTIFIER, BIT)
6. ✅ Support all features available in PostgreSQL version

The service is now ready for development and testing with MS SQL Server!
