# AGENTS.md

## Cursor Cloud specific instructions

### Overview
This is a **Position Management Service** — an event-sourced Java/Spring Boot backend for managing equity swap positions with Hotpath/Coldpath architecture. It is a multi-module Maven project (`domain`, `infrastructure`, `application`, `api`).

### Prerequisites (installed by update script)
- **Maven 3.8+** — build tool
- **Docker + Docker Compose** — required for infrastructure services

### Building
```bash
mvn clean install -Dmaven.test.skip=true
```
Use `-Dmaven.test.skip=true` because the `infrastructure` module has test files that reference a missing class (`PartitionAwareMessage`), causing test compilation to fail. Use `-DskipTests` only if you want to compile tests but not run them.

### Infrastructure Services
Start required services via Docker Compose:
```bash
docker compose up -d sqlserver redis zookeeper kafka
```
- **SQL Server** (port 1433): Takes ~30s to become ready. Healthcheck may show "unhealthy" in newer images because `/opt/mssql-tools/bin/sqlcmd` moved to `/opt/mssql-tools18/bin/sqlcmd`. The server is actually running fine.
- **Kafka** (port 9092) + **Zookeeper** (port 2181): Usually ready in ~15s.
- **Redis** (port 6379): Ready almost immediately.
- **Solace** is optional and only needed if `MESSAGING_PROVIDER=solace`.

### Database Setup
The database `equity_swap_db` must be created manually before the app starts:
```bash
docker exec position-mgmt-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U SA -P 'Test@123456' -C -Q "IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'equity_swap_db') CREATE DATABASE equity_swap_db;"
```

### Known Issue: Duplicate Flyway V3 Migration
The repo originally contained two `V3__*.sql` migration files, causing Flyway to fail on startup. The duplicate (`V3__convert_idempotency_to_memory_optimized.sql`) was removed — its content is identical to `V4__convert_idempotency_to_memory_optimized.sql`.

### Known Issue: Flyway V4 Mixed Transactions
The `V4__convert_idempotency_to_memory_optimized.sql` migration uses SQL Server memory-optimized table operations that mix transactional and non-transactional statements. Flyway rejects this by default. Workaround: either pre-seed the schema and `flyway_schema_history` table manually, or pass `--spring.flyway.validate-on-migrate=false` at startup.

### Running the Application
```bash
AUTHORIZATION_ALLOW_ANONYMOUS=true \
AUTHORIZATION_ENABLED=false \
mvn spring-boot:run -pl api -Dmaven.test.skip=true \
  -Dspring-boot.run.arguments="--spring.flyway.validate-on-migrate=false"
```
Key env vars:
- `AUTHORIZATION_ALLOW_ANONYMOUS=true` and `AUTHORIZATION_ENABLED=false` — disables IAM/auth (no IAM service available locally).
- `--spring.flyway.validate-on-migrate=false` — skips Flyway checksum validation for the V4 migration issue.

The app starts on **port 8080**.

### API Endpoints
- `POST /api/trades` — submit a trade (returns `201 Created` with position state)
- `GET /api/positions/{positionKey}` — query a position
- `GET /api/diagnostics/events/count` — event store count
- `GET /api/diagnostics/events/position/{positionKey}` — events for a position
- `GET /api/diagnostics/snapshot/{positionKey}` — position snapshot
- `GET /actuator/health` — health check (DB, Redis, disk)

### Tests
The only compilable tests are in the `infrastructure` module (partition-awareness tests for Kafka/Solace). Other test files exist in `.history/` but are editor history snapshots. Run tests with:
```bash
mvn test -pl infrastructure
```
Note: These tests require `PartitionAwareMessage` class to exist in the domain module, which is currently missing, so test compilation will fail.
