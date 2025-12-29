#!/bin/bash

set -e

echo "=========================================="
echo "End-to-End Test"
echo "=========================================="

BASE_URL="${BASE_URL:-http://localhost:8080}"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

test_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ PASS${NC}: $2"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}❌ FAIL${NC}: $2"
        ((TESTS_FAILED++))
    fi
}

echo ""
echo "1. Testing Health Endpoints..."
echo "----------------------------------------"

# Test liveness
if curl -s -f "${BASE_URL}/health/liveness" > /dev/null 2>&1; then
    test_result 0 "Liveness check"
else
    test_result 1 "Liveness check"
fi

# Test readiness
if curl -s -f "${BASE_URL}/health/readiness" > /dev/null 2>&1; then
    test_result 0 "Readiness check"
else
    test_result 1 "Readiness check"
fi

echo ""
echo "2. Testing Trade Processing..."
echo "----------------------------------------"

# Create a trade
TRADE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/trades" \
  -H "Content-Type: application/json" \
  -d '{
    "tradeId": "T001",
    "account": "ACC001",
    "instrument": "AAPL",
    "currency": "USD",
    "quantity": 100,
    "price": 150.00,
    "tradeDate": "2024-01-15",
    "effectiveDate": "2024-01-15"
  }')

HTTP_CODE=$(echo "$TRADE_RESPONSE" | tail -n1)
BODY=$(echo "$TRADE_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 201 ]; then
    test_result 0 "Process trade (POST /api/trades)"
    POSITION_KEY=$(echo "$BODY" | grep -o '"positionKey":"[^"]*"' | cut -d'"' -f4 || echo "ACC001:AAPL:USD")
    echo "   Position Key: $POSITION_KEY"
else
    test_result 1 "Process trade (POST /api/trades) - HTTP $HTTP_CODE"
    echo "   Response: $BODY"
    POSITION_KEY="ACC001:AAPL:USD"
fi

echo ""
echo "3. Testing Position Query..."
echo "----------------------------------------"

# Query position
POSITION_RESPONSE=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/positions/${POSITION_KEY}")
HTTP_CODE=$(echo "$POSITION_RESPONSE" | tail -n1)
BODY=$(echo "$POSITION_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    test_result 0 "Get position (GET /api/positions/{positionKey})"
    echo "   Position data: $(echo "$BODY" | head -c 200)..."
    
    # Verify position has data
    if echo "$BODY" | grep -q "positionKey"; then
        test_result 0 "Position contains expected data"
    else
        test_result 1 "Position missing expected data"
    fi
else
    test_result 1 "Get position (GET /api/positions/{positionKey}) - HTTP $HTTP_CODE"
    echo "   Response: $BODY"
fi

echo ""
echo "4. Testing Multiple Trades..."
echo "----------------------------------------"

# Process second trade
TRADE_RESPONSE2=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/trades" \
  -H "Content-Type: application/json" \
  -d '{
    "tradeId": "T002",
    "account": "ACC001",
    "instrument": "AAPL",
    "currency": "USD",
    "quantity": 50,
    "price": 155.00,
    "tradeDate": "2024-01-16",
    "effectiveDate": "2024-01-16"
  }')

HTTP_CODE2=$(echo "$TRADE_RESPONSE2" | tail -n1)
if [ "$HTTP_CODE2" -eq 201 ]; then
    test_result 0 "Process second trade"
    
    # Verify position updated
    POSITION_RESPONSE2=$(curl -s "${BASE_URL}/api/positions/${POSITION_KEY}")
    if echo "$POSITION_RESPONSE2" | grep -q "openLots"; then
        LOT_COUNT=$(echo "$POSITION_RESPONSE2" | grep -o '"lotId"' | wc -l || echo "0")
        if [ "$LOT_COUNT" -ge 2 ]; then
            test_result 0 "Position contains multiple tax lots ($LOT_COUNT lots)"
        else
            test_result 1 "Position should have multiple lots, found: $LOT_COUNT"
        fi
    else
        test_result 1 "Position missing openLots"
    fi
else
    test_result 1 "Process second trade - HTTP $HTTP_CODE2"
fi

echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "${GREEN}Tests Passed: ${TESTS_PASSED}${NC}"
echo -e "${RED}Tests Failed: ${TESTS_FAILED}${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed${NC}"
    exit 1
fi
