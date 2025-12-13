#!/bin/bash

set -e

echo "=========================================="
echo "End-to-End Integration Test"
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

echo -e "${YELLOW}Step 1: Starting infrastructure services (PostgreSQL, Kafka, Redis)...${NC}"
$COMPOSE_CMD up -d

echo -e "${YELLOW}Waiting for services to be healthy...${NC}"
sleep 10

# Wait for PostgreSQL
echo -e "${YELLOW}Waiting for PostgreSQL...${NC}"
until docker exec position-mgmt-postgres pg_isready -U postgres > /dev/null 2>&1; do
    echo -n "."
    sleep 1
done
echo -e " ${GREEN}PostgreSQL is ready${NC}"

# Wait for Kafka
echo -e "${YELLOW}Waiting for Kafka...${NC}"
until docker exec position-mgmt-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; do
    echo -n "."
    sleep 1
done
echo -e " ${GREEN}Kafka is ready${NC}"

# Wait for Redis
echo -e "${YELLOW}Waiting for Redis...${NC}"
until docker exec position-mgmt-redis redis-cli ping > /dev/null 2>&1; do
    echo -n "."
    sleep 1
done
echo -e " ${GREEN}Redis is ready${NC}"

echo -e "${YELLOW}Step 2: Running database migrations...${NC}"
# Note: Flyway will run migrations automatically when the app starts
# But we can also run them manually if needed

echo -e "${YELLOW}Step 3: Running end-to-end integration tests...${NC}"
echo -e "${YELLOW}Note: Tests will use Testcontainers (services may already be running)${NC}"
mvn clean test -pl application -Dtest=EndToEndIntegrationTest -Dspring.profiles.active=e2e

TEST_RESULT=$?

if [ $TEST_RESULT -eq 0 ]; then
    echo -e "${GREEN}=========================================="
    echo "✅ All E2E tests passed!"
    echo "==========================================${NC}"
    
    echo -e "${YELLOW}Do you want to keep the services running? (y/n)${NC}"
    read -r KEEP_SERVICES
    
    if [ "$KEEP_SERVICES" != "y" ]; then
        echo -e "${YELLOW}Stopping services...${NC}"
        $COMPOSE_CMD down
    else
        echo -e "${GREEN}Services are still running.${NC}"
        echo -e "To stop them, run: $COMPOSE_CMD down"
    fi
else
    echo -e "${RED}=========================================="
    echo "❌ E2E tests failed!"
    echo "==========================================${NC}"
    
    echo -e "${YELLOW}Keeping services running for debugging...${NC}"
    echo -e "To stop them, run: $COMPOSE_CMD down"
    
    exit $TEST_RESULT
fi
