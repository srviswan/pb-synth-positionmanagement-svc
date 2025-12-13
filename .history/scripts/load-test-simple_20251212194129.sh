#!/bin/bash

# Simplified Load Test Script
# Uses Apache Bench (ab) or curl for load testing

set -e

BASE_URL="${BASE_URL:-http://localhost:8081}"
TOTAL_REQUESTS="${TOTAL_REQUESTS:-1000}"
CONCURRENT_REQUESTS="${CONCURRENT_REQUESTS:-50}"

echo "=========================================="
echo "Position Management Service Load Test"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo "Total Requests: $TOTAL_REQUESTS"
echo "Concurrent Requests: $CONCURRENT_REQUESTS"
echo ""

# Check if Apache Bench is available
if command -v ab &> /dev/null; then
    echo "Using Apache Bench (ab) for load testing"
    echo ""
    
    # Create a test trade payload file
    PAYLOAD_FILE=$(mktemp)
    POS_KEY="LOAD-TEST-$(date +%s)"
    base_date=$(date +%Y-%m-%d)
    
    cat > "$PAYLOAD_FILE" <<EOF
{
    "tradeId": "LOAD-TEST-TRADE",
    "positionKey": "$POS_KEY",
    "tradeType": "NEW_TRADE",
    "quantity": 100,
    "price": 50.00,
    "effectiveDate": "$base_date",
    "contractId": "LOAD-CONTRACT-001",
    "correlationId": "CORR-LOAD",
    "causationId": "CAUS-LOAD",
    "userId": "LOAD-USER"
}
EOF
    
    echo "Running load test with Apache Bench..."
    ab -n $TOTAL_REQUESTS -c $CONCURRENT_REQUESTS -p "$PAYLOAD_FILE" -T "application/json" \
        "$BASE_URL/api/trades" 2>&1 | grep -E "(Requests per second|Time per request|Failed requests|Complete requests)"
    
    rm -f "$PAYLOAD_FILE"
    
elif command -v wrk &> /dev/null; then
    echo "Using wrk for load testing"
    echo ""
    
    # Create Lua script for wrk
    LUA_SCRIPT=$(mktemp)
    POS_KEY="LOAD-TEST-$(date +%s)"
    base_date=$(date +%Y-%m-%d)
    
    cat > "$LUA_SCRIPT" <<EOF
wrk.method = "POST"
wrk.body = '{"tradeId":"LOAD-TEST-TRADE","positionKey":"$POS_KEY","tradeType":"NEW_TRADE","quantity":100,"price":50.00,"effectiveDate":"$base_date","contractId":"LOAD-CONTRACT-001","correlationId":"CORR-LOAD","causationId":"CAUS-LOAD","userId":"LOAD-USER"}'
wrk.headers["Content-Type"] = "application/json"
EOF
    
    echo "Running load test with wrk..."
    wrk -t4 -c$CONCURRENT_REQUESTS -d30s -s "$LUA_SCRIPT" "$BASE_URL/api/trades"
    
    rm -f "$LUA_SCRIPT"
    
else
    echo "Apache Bench (ab) or wrk not found. Using curl-based load test..."
    echo ""
    
    # Fallback to curl-based load test
    POS_KEY="LOAD-TEST-$(date +%s)"
    base_date=$(date +%Y-%m-%d)
    
    echo "Running $TOTAL_REQUESTS requests with $CONCURRENT_REQUESTS concurrent connections..."
    
    start_time=$(date +%s%N)
    success_count=0
    error_count=0
    times=()
    
    # Function to make a request
    make_request() {
        local trade_id="LOAD-TEST-$(date +%s%N)"
        local start=$(date +%s%N)
        
        response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/trades" \
            -H "Content-Type: application/json" \
            -d "{
                \"tradeId\": \"$trade_id\",
                \"positionKey\": \"$POS_KEY\",
                \"tradeType\": \"NEW_TRADE\",
                \"quantity\": 100,
                \"price\": 50.00,
                \"effectiveDate\": \"$base_date\",
                \"contractId\": \"LOAD-CONTRACT-001\",
                \"correlationId\": \"CORR-LOAD\",
                \"causationId\": \"CAUS-LOAD\",
                \"userId\": \"LOAD-USER\"
            }" 2>&1)
        
        local end=$(date +%s%N)
        local http_code=$(echo "$response" | tail -1)
        local duration_ms=$(( (end - start) / 1000000 ))
        
        if [ "$http_code" = "200" ]; then
            echo "SUCCESS|$duration_ms"
        else
            echo "ERROR|$duration_ms|$http_code"
        fi
    }
    
    # Export function for parallel execution
    export -f make_request
    export BASE_URL POS_KEY base_date
    
    # Run requests in parallel
    results=$(seq 1 $TOTAL_REQUESTS | xargs -n1 -P$CONCURRENT_REQUESTS -I{} bash -c 'make_request')
    
    end_time=$(date +%s%N)
    duration_ms=$(( (end_time - start_time) / 1000000 ))
    
    # Analyze results
    success_count=$(echo "$results" | grep -c "^SUCCESS" || echo "0")
    error_count=$(echo "$results" | grep -c "^ERROR" || echo "0")
    
    times=$(echo "$results" | grep "^SUCCESS" | cut -d'|' -f2 | sort -n)
    
    if [ -n "$times" ]; then
        avg_time=$(echo "$times" | awk '{sum+=$1; count++} END {if(count>0) print sum/count; else print 0}')
        min_time=$(echo "$times" | head -1)
        max_time=$(echo "$times" | tail -1)
        
        count=$(echo "$times" | wc -l | tr -d ' ')
        p50_idx=$((count * 50 / 100))
        p95_idx=$((count * 95 / 100))
        p99_idx=$((count * 99 / 100))
        
        p50=$(echo "$times" | sed -n "${p50_idx}p" || echo "0")
        p95=$(echo "$times" | sed -n "${p95_idx}p" || echo "0")
        p99=$(echo "$times" | sed -n "${p99_idx}p" || echo "0")
        
        throughput=$(python3 -c "print(round($success_count * 1000 / $duration_ms, 2))" 2>/dev/null || echo "0")
        error_rate=$(python3 -c "print(round($error_count * 100 / $TOTAL_REQUESTS, 2))" 2>/dev/null || echo "0")
        
        echo ""
        echo "Results:"
        echo "  Total requests: $TOTAL_REQUESTS"
        echo "  Successful: $success_count"
        echo "  Errors: $error_count (${error_rate}%)"
        echo "  Total time: ${duration_ms}ms"
        echo "  Throughput: ${throughput} requests/second"
        echo "  Average latency: $(printf "%.2f" $avg_time)ms"
        echo "  Min latency: ${min_time}ms"
        echo "  Max latency: ${max_time}ms"
        echo "  P50 latency: ${p50}ms"
        echo "  P95 latency: ${p95}ms"
        echo "  P99 latency: ${p99}ms"
    else
        echo "No successful requests!"
    fi
fi

echo ""
echo "Load test completed!"
