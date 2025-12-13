# Position Management Service

Event-Sourced Position Management Service with Hotpath/Coldpath Architecture for processing 2M trades/day.

## Architecture

- **Hotpath**: Low-latency (<100ms p99) synchronous processing for current/forward-dated trades
- **Coldpath**: Asynchronous processing for backdated trades with full event stream replay
- **Event Sourcing**: Append-only event store with version-based optimistic locking
- **Snapshot Pattern**: Hot cache for fast state reconstruction

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Database**: PostgreSQL (partitioned event store)
- **Cache**: Redis
- **Messaging**: Apache Kafka
- **Schema Registry**: Confluent Schema Registry (Avro)
- **Resilience**: Resilience4j (Circuit breakers, retries, timeouts)
- **Observability**: OpenTelemetry, Micrometer, Prometheus
- **Build Tool**: Maven

## Project Structure

```
position-management-service/
├── domain/          # Core business logic and domain models
├── infrastructure/  # Database, Kafka, external integrations
├── application/     # Service layer and orchestration
└── api/            # REST endpoints and API layer
```

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 14+
- Redis 6+
- Kafka 3.5+
- Confluent Schema Registry

### Running the Application

1. Start infrastructure services (PostgreSQL, Redis, Kafka, Schema Registry)
2. Configure `application.yml` with connection details
3. Run migrations: Flyway will auto-migrate on startup
4. Start the application:
   ```bash
   mvn spring-boot:run -pl api
   ```

### Environment Variables

- `DB_USERNAME`: PostgreSQL username
- `DB_PASSWORD`: PostgreSQL password
- `REDIS_HOST`: Redis host
- `REDIS_PORT`: Redis port
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka brokers
- `SCHEMA_REGISTRY_URL`: Schema Registry URL
- `ENVIRONMENT`: Environment name (local, dev, prod)

## Development

### Building

```bash
mvn clean install
```

### Testing

```bash
mvn test
```

## Documentation

See `documents/IMPLEMENTATION_PLAN.md` for detailed implementation plan.

## License

Proprietary - Bank Internal Use Only
