#!/bin/bash

# Test script for Position Management Service endpoints
# Tests all PositionController endpoints

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
API_BASE="${BASE_URL}/api"

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0
TOTAL_TESTS=0

# Helper functions
print_test() {
    echo -e "${BLUE}▶ Testing: $1${NC}"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

print_success() {
    echo -e "${GREEN}✓ PASS: $1${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

print_failure() {
    echo -e "${RED}✗ FAIL: $1${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# Check if service is running
check_service() {
    print_info "Checking if service is running at ${BASE_URL}..."
    if curl -s -f "${BASE_URL}/actuator/health" > /dev/null 2>&1; then
        print_success "Service is running"
        return 0
    elif curl -s -f "${API_BASE}/positions" > /dev/null 2>&1; then
        print_success "Service is running (health endpoint not available, but API responds)"
        return 0
    else
        print_failure "Service is not running at ${BASE_URL}"
        print_info "Please start the service first: mvn spring-boot:run -pl api"
        print_info "Or run with Testcontainers: mvn test -pl api"
        return 1
    fi
}

# Create test data first (submit trades)
create_test_data() {
    print_info "Creating test data..."
    
    # Test account and instrument
    TEST_ACCOUNT="TEST-ACC-001"
    TEST_INSTRUMENT="AAPL"
    TEST_CURRENCY="USD"
    TEST_CONTRACT="TEST-CONTRACT-001"
    
    # Generate position key using SHA-256 (matching PositionKeyGenerator logic)
    # Format: account|instrument|currency|direction (normalized and hashed)
    NORMALIZED_ACCOUNT=$(echo "${TEST_ACCOUNT}" | tr '[:upper:]' '[:lower:]' | tr -d ' ' | tr -d '-')
    NORMALIZED_INSTRUMENT=$(echo "${TEST_INSTRUMENT}" | tr '[:upper:]' '[:lower:]' | tr -d ' ' | tr -d '-')
    NORMALIZED_CURRENCY=$(echo "${TEST_CURRENCY}" | tr '[:upper:]' '[:lower:]' | tr -d ' ')
    INPUT="${NORMALIZED_ACCOUNT}|${NORMALIZED_INSTRUMENT}|${NORMALIZED_CURRENCY}|long"
    POSITION_KEY=$(echo -n "${INPUT}" | sha256sum | cut -d' ' -f1)
    
    print_info "Test position key: ${POSITION_KEY}"
    
    # Submit a NEW_TRADE
    print_test "Creating new position via TradeController"
    TRADE_ID="TEST-TRADE-$(date +%s)-$$"
    TRADE_RESPONSE=$(curl -s -X POST "${API_BASE}/trades" \
        -H "Content-Type: application/json" \
        -d "{
            \"tradeId\": \"${TRADE_ID}\",
            \"positionKey\": \"${POSITION_KEY}\",
            \"account\": \"${TEST_ACCOUNT}\",
            \"instrument\": \"${TEST_INSTRUMENT}\",
            \"currency\": \"${TEST_CURRENCY}\",
            \"tradeType\": \"NEW_TRADE\",
            \"quantity\": 1000,
            \"price\": 150.50,
            \"effectiveDate\": \"$(date +%Y-%m-%d)\",
            \"contractId\": \"${TEST_CONTRACT}\",
            \"correlationId\": \"TEST-CORR-001\",
            \"causationId\": \"TEST-CAUS-001\",
            \"userId\": \"test-user\"
        }" 2>&1)
    
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API_BASE}/trades" \
        -H "Content-Type: application/json" \
        -d "{
            \"tradeId\": \"${TRADE_ID}\",
            \"positionKey\": \"${POSITION_KEY}\",
            \"account\": \"${TEST_ACCOUNT}\",
            \"instrument\": \"${TEST_INSTRUMENT}\",
            \"currency\": \"${TEST_CURRENCY}\",
            \"tradeType\": \"NEW_TRADE\",
            \"quantity\": 1000,
            \"price\": 150.50,
            \"effectiveDate\": \"$(date +%Y-%m-%d)\",
            \"contractId\": \"${TEST_CONTRACT}\",
            \"correlationId\": \"TEST-CORR-001\",
            \"causationId\": \"TEST-CAUS-001\",
            \"userId\": \"test-user\"
        }")
    
    if [ "${HTTP_CODE}" -eq 200 ] && echo "${TRADE_RESPONSE}" | grep -q "success"; then
        print_success "Trade submitted successfully"
        sleep 3  # Wait for processing
    else
        print_failure "Failed to submit trade (HTTP ${HTTP_CODE}): ${TRADE_RESPONSE}"
        print_info "Note: This may be expected if the service requires database setup. Continuing with tests..."
        # Don't return 1 - continue with tests that might work
    fi
    
    # Export for use in other tests
    export TEST_ACCOUNT TEST_INSTRUMENT TEST_CURRENCY TEST_CONTRACT POSITION_KEY
}

# Test 1: Get position by position key
test_get_position_by_key() {
    print_test "GET /api/positions/{positionKey}"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/${POSITION_KEY}")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 200 ]; then
        if echo "${BODY}" | grep -q "\"status\":\"success\"" && \
           echo "${BODY}" | grep -q "\"positionKey\":\"${POSITION_KEY}\""; then
            print_success "Get position by key"
            echo "${BODY}" | jq '.' 2>/dev/null || echo "${BODY}"
        else
            print_failure "Get position by key - invalid response"
        fi
    else
        print_failure "Get position by key - HTTP ${HTTP_CODE}"
    fi
}

# Test 2: Get position quantity
test_get_position_quantity() {
    print_test "GET /api/positions/{positionKey}/quantity"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/${POSITION_KEY}/quantity")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 200 ]; then
        if echo "${BODY}" | grep -q "\"quantity\""; then
            print_success "Get position quantity"
            echo "${BODY}" | jq '.' 2>/dev/null || echo "${BODY}"
        else
            print_failure "Get position quantity - invalid response"
        fi
    else
        print_failure "Get position quantity - HTTP ${HTTP_CODE}"
    fi
}

# Test 3: Get position details
test_get_position_details() {
    print_test "GET /api/positions/{positionKey}/details"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/${POSITION_KEY}/details")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 200 ]; then
        if echo "${BODY}" | grep -q "\"taxLots\""; then
            print_success "Get position details"
            echo "${BODY}" | jq '.' 2>/dev/null || echo "${BODY}"
        else
            print_failure "Get position details - invalid response"
        fi
    else
        print_failure "Get position details - HTTP ${HTTP_CODE}"
    fi
}

# Test 4: Get all positions (paginated)
test_get_all_positions() {
    print_test "GET /api/positions (paginated)"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions?page=0&size=10")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 200 ]; then
        if echo "${BODY}" | grep -q "\"pagination\""; then
            print_success "Get all positions (paginated)"
            echo "${BODY}" | jq '.pagination' 2>/dev/null || echo "${BODY}"
        else
            print_failure "Get all positions - missing pagination"
        fi
    else
        print_failure "Get all positions - HTTP ${HTTP_CODE}"
    fi
}

# Test 5: Get positions by account
test_get_positions_by_account() {
    print_test "GET /api/positions/by-account/{account}"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/by-account/${TEST_ACCOUNT}?page=0&size=10")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 200 ]; then
        if echo "${BODY}" | grep -q "\"pagination\""; then
            print_success "Get positions by account"
            echo "${BODY}" | jq '.pagination' 2>/dev/null || echo "${BODY}"
        else
            print_failure "Get positions by account - missing pagination"
        fi
    else
        print_failure "Get positions by account - HTTP ${HTTP_CODE}"
    fi
}

# Test 6: Get positions by account with status filter
test_get_positions_by_account_with_status() {
    print_test "GET /api/positions/by-account/{account}?status=ACTIVE"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/by-account/${TEST_ACCOUNT}?status=ACTIVE&page=0&size=10")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 200 ]; then
        print_success "Get positions by account with status filter"
        echo "${BODY}" | jq '.pagination' 2>/dev/null || echo "${BODY}"
    else
        print_failure "Get positions by account with status - HTTP ${HTTP_CODE}"
    fi
}

# Test 7: Get positions by instrument
test_get_positions_by_instrument() {
    print_test "GET /api/positions/by-instrument/{instrument}"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/by-instrument/${TEST_INSTRUMENT}?page=0&size=10")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 200 ]; then
        if echo "${BODY}" | grep -q "\"pagination\""; then
            print_success "Get positions by instrument"
            echo "${BODY}" | jq '.pagination' 2>/dev/null || echo "${BODY}"
        else
            print_failure "Get positions by instrument - missing pagination"
        fi
    else
        print_failure "Get positions by instrument - HTTP ${HTTP_CODE}"
    fi
}

# Test 8: Get positions by account and instrument
test_get_positions_by_account_and_instrument() {
    print_test "GET /api/positions/by-account/{account}/instrument/{instrument}"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/by-account/${TEST_ACCOUNT}/instrument/${TEST_INSTRUMENT}?page=0&size=10")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 200 ]; then
        if echo "${BODY}" | grep -q "\"pagination\""; then
            print_success "Get positions by account and instrument"
            echo "${BODY}" | jq '.pagination' 2>/dev/null || echo "${BODY}"
        else
            print_failure "Get positions by account and instrument - missing pagination"
        fi
    else
        print_failure "Get positions by account and instrument - HTTP ${HTTP_CODE}"
    fi
}

# Test 9: Get positions by account, instrument, and currency
test_get_positions_by_account_instrument_currency() {
    print_test "GET /api/positions/by-account/{account}/instrument/{instrument}?currency={currency}"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/by-account/${TEST_ACCOUNT}/instrument/${TEST_INSTRUMENT}?currency=${TEST_CURRENCY}&page=0&size=10")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 200 ]; then
        if echo "${BODY}" | grep -q "\"pagination\""; then
            print_success "Get positions by account, instrument, and currency"
            echo "${BODY}" | jq '.pagination' 2>/dev/null || echo "${BODY}"
        else
            print_failure "Get positions by account, instrument, and currency - missing pagination"
        fi
    else
        print_failure "Get positions by account, instrument, and currency - HTTP ${HTTP_CODE}"
    fi
}

# Test 10: Get positions by contract
test_get_positions_by_contract() {
    print_test "GET /api/positions/by-contract/{contractId}"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/by-contract/${TEST_CONTRACT}?page=0&size=10")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 200 ]; then
        if echo "${BODY}" | grep -q "\"pagination\""; then
            print_success "Get positions by contract"
            echo "${BODY}" | jq '.pagination' 2>/dev/null || echo "${BODY}"
        else
            print_failure "Get positions by contract - missing pagination"
        fi
    else
        print_failure "Get positions by contract - HTTP ${HTTP_CODE}"
    fi
}

# Test 11: Get position by UPI
test_get_position_by_upi() {
    print_test "GET /api/positions/upi/{upi}"
    
    # First get the position to find its UPI
    POS_RESPONSE=$(curl -s "${API_BASE}/positions/${POSITION_KEY}")
    UPI=$(echo "${POS_RESPONSE}" | grep -o '"upi":"[^"]*"' | cut -d'"' -f4 || echo "")
    
    if [ -n "${UPI}" ]; then
        RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/upi/${UPI}")
        HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
        BODY=$(echo "${RESPONSE}" | head -n-1)
        
        if [ "${HTTP_CODE}" -eq 200 ]; then
            if echo "${BODY}" | grep -q "\"upi\":\"${UPI}\""; then
                print_success "Get position by UPI"
                echo "${BODY}" | jq '.' 2>/dev/null || echo "${BODY}"
            else
                print_failure "Get position by UPI - invalid response"
            fi
        else
            print_failure "Get position by UPI - HTTP ${HTTP_CODE}"
        fi
    else
        print_failure "Get position by UPI - could not extract UPI from position"
    fi
}

# Test 12: Test pagination parameters
test_pagination_parameters() {
    print_test "Testing pagination parameters (page, size, sortBy, sortDir)"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/by-account/${TEST_ACCOUNT}?page=0&size=5&sortBy=lastUpdatedAt&sortDir=DESC")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 200 ]; then
        if echo "${BODY}" | grep -q "\"size\":5" && \
           echo "${BODY}" | grep -q "\"page\":0"; then
            print_success "Pagination parameters working"
            echo "${BODY}" | jq '.pagination' 2>/dev/null || echo "${BODY}"
        else
            print_failure "Pagination parameters - invalid response"
        fi
    else
        print_failure "Pagination parameters - HTTP ${HTTP_CODE}"
    fi
}

# Test 13: Test error cases
test_error_cases() {
    print_test "Testing error cases (404 for non-existent position)"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/non-existent-key-12345")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 404 ]; then
        if echo "${BODY}" | grep -q "not_found"; then
            print_success "Error handling (404) working correctly"
        else
            print_failure "Error handling - invalid error response"
        fi
    else
        print_failure "Error handling - expected 404, got ${HTTP_CODE}"
    fi
}

# Test 14: Test invalid status parameter
test_invalid_status() {
    print_test "Testing invalid status parameter"
    
    RESPONSE=$(curl -s -w "\n%{http_code}" "${API_BASE}/positions/by-account/${TEST_ACCOUNT}?status=INVALID_STATUS")
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | head -n-1)
    
    if [ "${HTTP_CODE}" -eq 400 ]; then
        if echo "${BODY}" | grep -q "error"; then
            print_success "Invalid status parameter validation working"
        else
            print_failure "Invalid status - invalid error response"
        fi
    else
        print_failure "Invalid status - expected 400, got ${HTTP_CODE}"
    fi
}

# Main execution
main() {
    echo "=========================================="
    echo "Position Management Service - Endpoint Tests"
    echo "=========================================="
    echo ""
    
    # Check if service is running
    if ! check_service; then
        exit 1
    fi
    
    echo ""
    
    # Create test data
    create_test_data
    
    echo ""
    echo "=========================================="
    echo "Running Endpoint Tests"
    echo "=========================================="
    echo ""
    
    # Run all tests
    test_get_position_by_key
    echo ""
    
    test_get_position_quantity
    echo ""
    
    test_get_position_details
    echo ""
    
    test_get_all_positions
    echo ""
    
    test_get_positions_by_account
    echo ""
    
    test_get_positions_by_account_with_status
    echo ""
    
    test_get_positions_by_instrument
    echo ""
    
    test_get_positions_by_account_and_instrument
    echo ""
    
    test_get_positions_by_account_instrument_currency
    echo ""
    
    test_get_positions_by_contract
    echo ""
    
    test_get_position_by_upi
    echo ""
    
    test_pagination_parameters
    echo ""
    
    test_error_cases
    echo ""
    
    test_invalid_status
    echo ""
    
    # Summary
    echo "=========================================="
    echo "Test Summary"
    echo "=========================================="
    echo -e "Total Tests: ${TOTAL_TESTS}"
    echo -e "${GREEN}Passed: ${TESTS_PASSED}${NC}"
    echo -e "${RED}Failed: ${TESTS_FAILED}${NC}"
    echo ""
    
    if [ ${TESTS_FAILED} -eq 0 ]; then
        echo -e "${GREEN}All tests passed! ✓${NC}"
        exit 0
    else
        echo -e "${RED}Some tests failed! ✗${NC}"
        exit 1
    fi
}

# Run main function
main

