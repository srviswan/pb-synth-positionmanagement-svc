# End-to-End Integration Testing

This document describes how to run end-to-end integration tests for the Position Management Service.

## Overview

The E2E tests use **Testcontainers** to spin up real instances of:
- PostgreSQL 14 (for event store and snapshots)
- Apache Kafka 7.5.0 (for event streaming)
- Redis (optional, for caching)

Tests run against these real services to verify the complete hotpath flow.

## Prerequisites

1. **Docker** must be installed and running
2. **Maven 3.8+** 
3. **Java 17+**

## Quick Start

### Option 1: Using Testcontainers (Recommended)

Testcontainers will automatically start and stop containers for each test run:

```bash
# Run E2E tests (Testcontainers will manage services)
mvn clean test -pl application -Dtest=EndToEndIntegrationTest -Dspring.profiles.active=e2e
```

### Option 2: Using Docker Compose (Manual)

If you want to run services manually and keep them running:

1. **Start services:**
   ```bash
   ./scripts/start-services.sh
   # OR
   docker-compose up -d
   ```

2. **Run tests:**
   ```bash
   mvn clean test -pl application -Dtest=EndToEndIntegrationTest -Dspring.profiles.active=e2e
   ```

3. **Stop services (when done):**
   ```bash
   docker-compose down
   ```

### Option 3: Full E2E Script

Run the complete E2E test script that starts services, runs tests, and cleans up:

```bash
./scripts/run-e2e-test.sh
```

## Test Scenarios

The E2E tests cover:

1. **New Trade Flow**
   - Validates trade processing
   - Verifies event storage in PostgreSQL
   - Verifies snapshot creation
   - Checks reconciliation status

2. **Increase and Decrease Flow**
   - Processes multiple trades sequentially
   - Verifies event versioning
   - Verifies tax lot allocation
   - Checks final position state

3. **Backdated Trade Flow**
   - Classifies backdated trades correctly
   - Verifies routing to coldpath topic
   - (Full coldpath processing would be tested separately)

## Service Endpoints

When services are running via Docker Compose:

- **PostgreSQL**: `localhost:5432`
  - Database: `equity_swap_db`
  - Username: `postgres`
  - Password: `postgres`

- **Kafka**: `localhost:9092`
  - Topics are auto-created

- **Schema Registry**: `http://localhost:8081`

- **Redis**: `localhost:6379`

## Troubleshooting

### Docker Not Running
```
Error: Docker is not running
```
**Solution**: Start Docker Desktop or Docker daemon

### Port Already in Use
```
Error: Bind for 0.0.0.0:5432 failed: port is already allocated
```
**Solution**: Stop existing PostgreSQL/Kafka services or change ports in `docker-compose.yml`

### Testcontainers Can't Pull Images
```
Error: Could not find image
```
**Solution**: Ensure Docker has internet access to pull images

### Tests Timeout
```
Timeout waiting for assertion
```
**Solution**: 
- Check if services are healthy: `docker ps`
- Increase timeout in test code
- Check application logs

## Test Configuration

E2E tests use the `e2e` Spring profile defined in:
- `api/src/test/resources/application-e2e.yml`

This profile:
- Configures database connection (overridden by Testcontainers)
- Configures Kafka connection (overridden by Testcontainers)
- Enables debug logging
- Sets up test-specific properties

## Manual Testing

You can also test manually by:

1. Starting services: `./scripts/start-services.sh`
2. Starting the application:
   ```bash
   mvn spring-boot:run -pl api
   ```
3. Sending test events via Kafka or REST API
4. Checking database state:
   ```bash
   docker exec -it position-mgmt-postgres psql -U postgres -d equity_swap_db
   ```

## Next Steps

- Add more E2E scenarios (coldpath, reconciliation, etc.)
- Add performance/load testing
- Add chaos testing (service failures, network partitions)
- Add contract testing with Pact
