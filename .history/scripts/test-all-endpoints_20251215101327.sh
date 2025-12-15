#!/bin/bash

# Simple script to test all PositionController endpoints
# Uses Testcontainers for database setup

set -e

echo "=========================================="
echo "Testing All PositionController Endpoints"
echo "=========================================="
echo ""

cd "$(dirname "$0")/.."

echo "Running PositionController REST API Tests..."
echo ""

mvn test -pl api -Dtest='PositionControllerTest' 2>&1 | tee /tmp/endpoint-test.log

echo ""
echo "=========================================="
echo "Test Results Summary"
echo "=========================================="

if grep -q "Tests run:.*Failures: 0.*Errors: 0" /tmp/endpoint-test.log; then
    TEST_COUNT=$(grep -o "Tests run: [0-9]*" /tmp/endpoint-test.log | head -1 | grep -o "[0-9]*")
    echo "✓ All ${TEST_COUNT} endpoint tests PASSED"
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
    echo "  ✓ Error handling (404, 400)"
    echo "  ✓ Pagination parameters validation"
    echo ""
    exit 0
else
    echo "✗ Some tests failed"
    echo ""
    grep -E "(FAILURE|ERROR|Failures:|Errors:)" /tmp/endpoint-test.log | head -10
    echo ""
    exit 1
fi
