#!/bin/bash

# Test script for MS SQL Server integration
# Runs SqlServerIntegrationTest with Testcontainers

set -e

echo "=========================================="
echo "Testing MS SQL Server Integration"
echo "=========================================="
echo ""

cd "$(dirname "$0")/.."

echo "Running SqlServerIntegrationTest..."
echo ""

mvn test -pl api -Dtest='SqlServerIntegrationTest' 2>&1 | tee /tmp/sqlserver-test.log

echo ""
echo "=========================================="
echo "Test Results"
echo "=========================================="

if grep -q "Tests run:.*Failures: 0.*Errors: 0" /tmp/sqlserver-test.log; then
    TEST_COUNT=$(grep -o "Tests run: [0-9]*" /tmp/sqlserver-test.log | head -1 | grep -o "[0-9]*")
    echo "✓ All ${TEST_COUNT} MS SQL Server tests PASSED"
    echo ""
    echo "Verified:"
    echo "  ✓ MS SQL Server connection"
    echo "  ✓ Schema creation via Flyway"
    echo "  ✓ Position creation"
    echo "  ✓ Event storage"
    echo "  ✓ Lookup fields (account, instrument, currency, contractId)"
    echo "  ✓ Position lifecycle"
    echo ""
    exit 0
else
    echo "✗ Some tests failed"
    echo ""
    grep -E "(FAILURE|ERROR|Failures:|Errors:)" /tmp/sqlserver-test.log | head -10
    echo ""
    exit 1
fi
