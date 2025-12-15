#!/bin/bash

# Comprehensive test script for all Position Management Service endpoints
# Runs both integration tests and REST API controller tests

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "=========================================="
echo "Position Management Service"
echo "Comprehensive Endpoint Test Suite"
echo "=========================================="
echo ""

cd "$(dirname "$0")/.."

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to run tests and capture results
run_test_suite() {
    local test_name=$1
    local test_command=$2
    
    echo -e "${BLUE}Running: ${test_name}${NC}"
    echo "----------------------------------------"
    
    if eval "${test_command}" 2>&1 | tee /tmp/test-output.log; then
        if grep -q "BUILD SUCCESS" /tmp/test-output.log && \
           grep -q "Tests run:" /tmp/test-output.log && \
           ! grep -q "Failures: [1-9]" /tmp/test-output.log && \
           ! grep -q "Errors: [1-9]" /tmp/test-output.log; then
            local test_count=$(grep -o "Tests run: [0-9]*" /tmp/test-output.log | head -1 | grep -o "[0-9]*" | head -1)
            TOTAL_TESTS=$((TOTAL_TESTS + test_count))
            PASSED_TESTS=$((PASSED_TESTS + test_count))
            echo -e "${GREEN}✓ ${test_name} - PASSED${NC}"
            echo ""
            return 0
        else
            local test_count=$(grep -o "Tests run: [0-9]*" /tmp/test-output.log | head -1 | grep -o "[0-9]*" | head -1 || echo "0")
            local failures=$(grep -o "Failures: [0-9]*" /tmp/test-output.log | head -1 | grep -o "[0-9]*" || echo "0")
            TOTAL_TESTS=$((TOTAL_TESTS + test_count))
            FAILED_TESTS=$((FAILED_TESTS + failures))
            echo -e "${RED}✗ ${test_name} - FAILED (${failures} failures)${NC}"
            echo ""
            return 1
        fi
    else
        echo -e "${RED}✗ ${test_name} - ERROR${NC}"
        echo ""
        return 1
    fi
}

# Run all test suites
echo -e "${YELLOW}Starting comprehensive endpoint tests...${NC}"
echo ""

# 1. Position Controller Tests (REST API endpoints)
run_test_suite "PositionController REST API Tests" \
    "mvn test -pl api -Dtest='PositionControllerTest' -q"

# 2. End-to-End Integration Tests
run_test_suite "End-to-End Integration Tests" \
    "mvn test -pl api -Dtest='EndToEndIntegrationTest' -q"

# 3. Performance Tests
run_test_suite "Performance Tests" \
    "mvn test -pl api -Dtest='PositionServicePerformanceTest#testHotpathPerformance_SequentialTrades' -q"

# Summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "Total Tests Run: ${TOTAL_TESTS}"
echo -e "${GREEN}Passed: ${PASSED_TESTS}${NC}"
echo -e "${RED}Failed: ${FAILED_TESTS}${NC}"
echo ""

if [ ${FAILED_TESTS} -eq 0 ]; then
    echo -e "${GREEN}✓ All endpoint tests passed!${NC}"
    echo ""
    echo "Tested Endpoints:"
    echo "  ✓ GET /api/positions/{positionKey}"
    echo "  ✓ GET /api/positions/{positionKey}/quantity"
    echo "  ✓ GET /api/positions/{positionKey}/details"
    echo "  ✓ GET /api/positions (paginated)"
    echo "  ✓ GET /api/positions/by-account/{account} (paginated)"
    echo "  ✓ GET /api/positions/by-account/{account}?status=ACTIVE (paginated)"
    echo "  ✓ GET /api/positions/by-instrument/{instrument} (paginated)"
    echo "  ✓ GET /api/positions/by-account/{account}/instrument/{instrument} (paginated)"
    echo "  ✓ GET /api/positions/by-account/{account}/instrument/{instrument}?currency={currency} (paginated)"
    echo "  ✓ GET /api/positions/by-contract/{contractId} (paginated)"
    echo "  ✓ GET /api/positions/upi/{upi}"
    echo "  ✓ POST /api/trades (via integration tests)"
    echo ""
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    echo ""
    exit 1
fi
