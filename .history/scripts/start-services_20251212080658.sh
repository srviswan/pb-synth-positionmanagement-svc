#!/bin/bash

set -e

echo "=========================================="
echo "Starting Infrastructure Services"
echo "=========================================="

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running. Please start Docker and try again.${NC}"
    exit 1
fi

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${RED}Error: docker-compose is not available.${NC}"
    exit 1
fi

COMPOSE_CMD="docker-compose"
if docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
fi

# Get the project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

echo -e "${YELLOW}Starting services (PostgreSQL, Kafka, Zookeeper, Schema Registry, Redis)...${NC}"
$COMPOSE_CMD up -d

echo -e "${YELLOW}Waiting for services to be healthy...${NC}"
sleep 5

# Wait for PostgreSQL
echo -e "${YELLOW}Waiting for PostgreSQL...${NC}"
until docker exec position-mgmt-postgres pg_isready -U postgres > /dev/null 2>&1; do
    echo -n "."
    sleep 1
done
echo -e " ${GREEN}✓ PostgreSQL is ready${NC}"

# Wait for Kafka
echo -e "${YELLOW}Waiting for Kafka...${NC}"
until docker exec position-mgmt-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; do
    echo -n "."
    sleep 1
done
echo -e " ${GREEN}✓ Kafka is ready${NC}"

# Wait for Redis
echo -e "${YELLOW}Waiting for Redis...${NC}"
until docker exec position-mgmt-redis redis-cli ping > /dev/null 2>&1; do
    echo -n "."
    sleep 1
done
echo -e " ${GREEN}✓ Redis is ready${NC}"

# Wait for Schema Registry
echo -e "${YELLOW}Waiting for Schema Registry...${NC}"
until curl -f http://localhost:8081/subjects > /dev/null 2>&1; do
    echo -n "."
    sleep 1
done
echo -e " ${GREEN}✓ Schema Registry is ready${NC}"

echo -e "${GREEN}=========================================="
echo "✅ All services are ready!"
echo "==========================================${NC}"
echo ""
echo "Services running:"
echo "  - PostgreSQL: localhost:5432"
echo "  - Kafka: localhost:9092"
echo "  - Schema Registry: http://localhost:8081"
echo "  - Redis: localhost:6379"
echo ""
echo "To stop services, run: $COMPOSE_CMD down"
echo "To view logs, run: $COMPOSE_CMD logs -f"
