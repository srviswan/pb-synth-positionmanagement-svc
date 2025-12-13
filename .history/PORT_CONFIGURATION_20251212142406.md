# Port Configuration Explanation

## Summary

**No, the application and test containers are NOT using the same ports.**

## How It Works

### E2E Tests (Testcontainers) - Random Ports ✅

```java
@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    // Returns: jdbc:postgresql://localhost:54321 (random port)
    
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    // Returns: localhost:54322 (random port)
}
```

- **Testcontainers automatically assigns random available ports**
- Ports are dynamically injected via `@DynamicPropertySource`
- **No conflicts** with existing services
- Each test run gets fresh containers with new ports

### Application (Direct Run) - Fixed Ports ⚠️

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/equity_swap_db  # Fixed: 5432
  kafka:
    bootstrap-servers: localhost:9092  # Fixed: 9092
  data:
    redis:
      port: 6379  # Fixed: 6379
server:
  port: 8080  # Fixed: 8080
```

- Uses **fixed ports** from `application.yml`
- **Conflicts** if ports are already in use
- This is why startup failed: port 8080 was already in use

## Why Tests Pass But App Fails

| Scenario | Ports Used | Result |
|----------|-----------|--------|
| **E2E Tests** | Random (54321, 54322, etc.) | ✅ No conflicts |
| **Application** | Fixed (5432, 9092, 8080) | ❌ Conflicts with existing services |

## Solution: Use Environment Variables

The application now supports environment variables for flexible port configuration:

```bash
# Run with custom ports
DB_HOST=localhost DB_PORT=5433 \
KAFKA_HOST=localhost KAFKA_PORT=9093 \
REDIS_PORT=6380 \
SERVER_PORT=8081 \
mvn spring-boot:run -pl api
```

Or use existing services:

```bash
# Use existing Kafka on 9092
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
mvn spring-boot:run -pl api
```

## Port Configuration Priority

1. **Environment Variables** (highest priority)
   - `DB_HOST`, `DB_PORT`, `DB_NAME`
   - `KAFKA_BOOTSTRAP_SERVERS` or `KAFKA_HOST` + `KAFKA_PORT`
   - `REDIS_HOST`, `REDIS_PORT`
   - `SERVER_PORT`

2. **application.yml** (defaults)
   - Used if environment variables are not set

3. **Testcontainers** (tests only)
   - Overrides everything via `@DynamicPropertySource`

## Example: Running Application with Different Ports

```bash
# Option 1: Use different ports
SERVER_PORT=8081 \
DB_PORT=5433 \
KAFKA_PORT=9093 \
REDIS_PORT=6380 \
mvn spring-boot:run -pl api

# Option 2: Use existing services (if compatible)
# Just ensure ports match what's already running
mvn spring-boot:run -pl api

# Option 3: Check what's running first
lsof -i :8080  # Check what's on port 8080
lsof -i :9092  # Check what's on port 9092
lsof -i :5432  # Check what's on port 5432
```

## Testcontainers Port Assignment

When Testcontainers starts containers:

```java
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine");

// Internally, Testcontainers:
// 1. Finds random available port (e.g., 54321)
// 2. Maps container's internal 5432 -> host's 54321
// 3. getJdbcUrl() returns: jdbc:postgresql://localhost:54321/...
```

This ensures:
- ✅ No port conflicts
- ✅ Parallel test execution possible
- ✅ Clean isolation between test runs

## Summary

- **Tests**: Use random ports via Testcontainers → No conflicts ✅
- **Application**: Uses fixed ports → Can conflict ⚠️
- **Solution**: Use environment variables for flexible configuration ✅
