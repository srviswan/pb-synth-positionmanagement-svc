#!/bin/bash

# Test script for Position Management Service endpoints using Testcontainers
# This script runs the integration tests which include endpoint testing

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "=========================================="
echo "Position Management Service - Endpoint Tests"
echo "Using Testcontainers (PostgreSQL + Kafka)"
echo "=========================================="
echo ""

# Run the end-to-end integration tests which test the full flow
echo -e "${BLUE}Running End-to-End Integration Tests...${NC}"
echo ""

cd "$(dirname "$0")/.."

# Run the integration tests
mvn test -pl api -Dtest='EndToEndIntegrationTest' 2>&1 | tee /tmp/endpoint-test-output.log

# Extract test results
if grep -q "Tests run:.*Failures: 0.*Errors: 0" /tmp/endpoint-test-output.log; then
    echo ""
    echo -e "${GREEN}✓ All integration tests passed!${NC}"
    echo ""
    echo "Tested endpoints:"
    echo "  - POST /api/trades (Trade submission)"
    echo "  - GET /api/positions/{positionKey} (via test assertions)"
    echo "  - Event store queries"
    echo "  - Snapshot queries"
    echo ""
    exit 0
else
    echo ""
    echo -e "${YELLOW}Some tests failed. Check the output above for details.${NC}"
    echo ""
    exit 1
fi
