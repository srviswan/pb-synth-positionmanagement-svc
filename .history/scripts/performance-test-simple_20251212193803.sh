#!/bin/bash

# Simplified Performance Test Script
# Runs performance tests via REST API

set -e

BASE_URL="${BASE_URL:-http://localhost:8081}"
POS_KEY="PERF-$(date +%s)"

echo "=========================================="
echo "Position Management Service Performance Test"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo "Position Key: $POS_KEY"
echo ""

# Test 1: Hotpath Performance
echo "=== Test 1: Hotpath Performance - 50 Sequential Trades ==="
start_time=$(date +%s%N)

for i in {0..49}; do
    trade_type=$([ $i -eq 0 ] && echo "NEW_TRADE" || ([ $((i % 2)) -eq 0 ] && echo "INCREASE" || echo "DECREASE"))
    qty=$([ $((i % 2)) -eq 0 ] && echo "100" || echo "50")
    price=$(python3 -c "print(50.00 + $i * 0.1)")
    effective_date=$(date -v+${i}d +%Y-%m-%d 2>/dev/null || date -d "+${i} days" +%Y-%m-%d 2>/dev/null || echo "2024-01-01")
    
    curl -s -X POST "$BASE_URL/api/trades" \
        -H "Content-Type: application/json" \
        -d "{
            \"tradeId\": \"HOTPATH-T-$i\",
            \"positionKey\": \"$POS_KEY\",
            \"tradeType\": \"$trade_type\",
            \"quantity\": $qty,
            \"price\": $price,
            \"effectiveDate\": \"$effective_date\",
            \"contractId\": \"PERF-CONTRACT-001\",
            \"correlationId\": \"CORR-PERF-001\",
            \"causationId\": \"CAUS-PERF-001\",
            \"userId\": \"PERF-USER\"
        }" > /dev/null
    
    if [ $((i % 10)) -eq 0 ]; then
        echo "  Processed trade $i"
    fi
done

end_time=$(date +%s%N)
duration_ms=$(( (end_time - start_time) / 1000000 ))
avg_latency=$(python3 -c "print(round($duration_ms / 50, 2))")
throughput=$(python3 -c "print(round(50 * 1000 / $duration_ms, 2))")

echo "  Total trades: 50"
echo "  Total time: ${duration_ms}ms"
echo "  Average latency: ${avg_latency}ms"
echo "  Throughput: ${throughput} trades/second"
echo ""

# Test 2: Coldpath Performance
echo "=== Test 2: Coldpath Performance - Backdated Trade Recalculation ==="
POS_KEY_2="${POS_KEY}-COLD"

# Create position with forward-dated trades
echo "  Creating position with 10 forward-dated trades..."
for i in {0..9}; do
    effective_date=$(date -v-10d -v+${i}d +%Y-%m-%d 2>/dev/null || date -d "-10 days +${i} days" +%Y-%m-%d 2>/dev/null || echo "2024-01-01")
    
    curl -s -X POST "$BASE_URL/api/trades" \
        -H "Content-Type: application/json" \
        -d "{
            \"tradeId\": \"FORWARD-T-$i\",
            \"positionKey\": \"$POS_KEY_2\",
            \"tradeType\": \"$([ $i -eq 0 ] && echo 'NEW_TRADE' || echo 'INCREASE')\",
            \"quantity\": 100,
            \"price\": 50.00,
            \"effectiveDate\": \"$effective_date\",
            \"contractId\": \"PERF-CONTRACT-002\",
            \"correlationId\": \"CORR-PERF-002\",
            \"causationId\": \"CAUS-PERF-002\",
            \"userId\": \"PERF-USER\"
        }" > /dev/null
done

echo "  Processing 5 backdated trades..."
start_time=$(date +%s%N)
for i in {0..4}; do
    effective_date=$(date -v-10d -v+$((i * 2))d +%Y-%m-%d 2>/dev/null || date -d "-10 days +$((i * 2)) days" +%Y-%m-%d 2>/dev/null || echo "2024-01-01")
    
    curl -s -X POST "$BASE_URL/api/diagnostics/recalculate" \
        -H "Content-Type: application/json" \
        -d "{
            \"tradeId\": \"BACKDATED-T-$i\",
            \"positionKey\": \"$POS_KEY_2\",
            \"tradeType\": \"INCREASE\",
            \"quantity\": 50,
            \"price\": 45.00,
            \"effectiveDate\": \"$effective_date\",
            \"sequenceStatus\": \"BACKDATED\",
            \"contractId\": \"PERF-CONTRACT-002\",
            \"correlationId\": \"CORR-PERF-002\",
            \"causationId\": \"CAUS-PERF-002\",
            \"userId\": \"PERF-USER\"
        }" > /dev/null
    
    echo "    Processed backdated trade $i"
done
end_time=$(date +%s%N)
duration_ms=$(( (end_time - start_time) / 1000000 ))
avg_latency=$(python3 -c "print(round($duration_ms / 5, 2))")

echo "  Total backdated trades: 5"
echo "  Total time: ${duration_ms}ms"
echo "  Average latency: ${avg_latency}ms"
echo ""

# Test 3: Position Lifecycle
echo "=== Test 3: Position Lifecycle Performance ==="
POS_KEY_3="${POS_KEY}-LIFECYCLE"
base_date=$(date +%Y-%m-%d)

latencies=()
operations=("Create" "Add" "Partial Close" "Full Close" "Reopen")

# Create
start_time=$(date +%s%N)
curl -s -X POST "$BASE_URL/api/trades" \
    -H "Content-Type: application/json" \
    -d "{
        \"tradeId\": \"LIFECYCLE-NEW\",
        \"positionKey\": \"$POS_KEY_3\",
        \"tradeType\": \"NEW_TRADE\",
        \"quantity\": 1000,
        \"price\": 50.00,
        \"effectiveDate\": \"$base_date\",
        \"contractId\": \"PERF-CONTRACT-003\",
        \"correlationId\": \"CORR-LIFECYCLE\",
        \"causationId\": \"CAUS-LIFECYCLE\",
        \"userId\": \"PERF-USER\"
    }" > /dev/null
latencies+=($(( ($(date +%s%N) - start_time) / 1000000 )))

# Add
start_time=$(date +%s%N)
date_1=$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d "+1 day" +%Y-%m-%d 2>/dev/null || echo "2024-01-02")
curl -s -X POST "$BASE_URL/api/trades" \
    -H "Content-Type: application/json" \
    -d "{
        \"tradeId\": \"LIFECYCLE-INCREASE\",
        \"positionKey\": \"$POS_KEY_3\",
        \"tradeType\": \"INCREASE\",
        \"quantity\": 500,
        \"price\": 55.00,
        \"effectiveDate\": \"$date_1\",
        \"contractId\": \"PERF-CONTRACT-003\",
        \"correlationId\": \"CORR-LIFECYCLE\",
        \"causationId\": \"CAUS-LIFECYCLE\",
        \"userId\": \"PERF-USER\"
    }" > /dev/null
latencies+=($(( ($(date +%s%N) - start_time) / 1000000 )))

# Partial Close
start_time=$(date +%s%N)
date_2=$(date -v+2d +%Y-%m-%d 2>/dev/null || date -d "+2 days" +%Y-%m-%d 2>/dev/null || echo "2024-01-03")
curl -s -X POST "$BASE_URL/api/trades" \
    -H "Content-Type: application/json" \
    -d "{
        \"tradeId\": \"LIFECYCLE-PARTIAL\",
        \"positionKey\": \"$POS_KEY_3\",
        \"tradeType\": \"DECREASE\",
        \"quantity\": 500,
        \"price\": 60.00,
        \"effectiveDate\": \"$date_2\",
        \"contractId\": \"PERF-CONTRACT-003\",
        \"correlationId\": \"CORR-LIFECYCLE\",
        \"causationId\": \"CAUS-LIFECYCLE\",
        \"userId\": \"PERF-USER\"
    }" > /dev/null
latencies+=($(( ($(date +%s%N) - start_time) / 1000000 )))

# Full Close
start_time=$(date +%s%N)
date_3=$(date -v+3d +%Y-%m-%d 2>/dev/null || date -d "+3 days" +%Y-%m-%d 2>/dev/null || echo "2024-01-04")
curl -s -X POST "$BASE_URL/api/trades" \
    -H "Content-Type: application/json" \
    -d "{
        \"tradeId\": \"LIFECYCLE-FULL\",
        \"positionKey\": \"$POS_KEY_3\",
        \"tradeType\": \"DECREASE\",
        \"quantity\": 1000,
        \"price\": 65.00,
        \"effectiveDate\": \"$date_3\",
        \"contractId\": \"PERF-CONTRACT-003\",
        \"correlationId\": \"CORR-LIFECYCLE\",
        \"causationId\": \"CAUS-LIFECYCLE\",
        \"userId\": \"PERF-USER\"
    }" > /dev/null
latencies+=($(( ($(date +%s%N) - start_time) / 1000000 )))

# Reopen
start_time=$(date +%s%N)
date_4=$(date -v+4d +%Y-%m-%d 2>/dev/null || date -d "+4 days" +%Y-%m-%d 2>/dev/null || echo "2024-01-05")
curl -s -X POST "$BASE_URL/api/trades" \
    -H "Content-Type: application/json" \
    -d "{
        \"tradeId\": \"LIFECYCLE-REOPEN\",
        \"positionKey\": \"$POS_KEY_3\",
        \"tradeType\": \"NEW_TRADE\",
        \"quantity\": 200,
        \"price\": 70.00,
        \"effectiveDate\": \"$date_4\",
        \"contractId\": \"PERF-CONTRACT-003\",
        \"correlationId\": \"CORR-LIFECYCLE\",
        \"causationId\": \"CAUS-LIFECYCLE\",
        \"userId\": \"PERF-USER\"
    }" > /dev/null
latencies+=($(( ($(date +%s%N) - start_time) / 1000000 )))

total=0
for lat in "${latencies[@]}"; do
    total=$((total + lat))
done
avg_latency=$(python3 -c "print(round($total / 5, 2))")

echo "  Operations: ${operations[*]}"
echo "  Latencies: ${latencies[*]}ms"
echo "  Average latency: ${avg_latency}ms"
echo ""

# Summary
echo "=========================================="
echo "Performance Test Summary"
echo "=========================================="
echo "Test 1 (Hotpath Sequential): ${avg_latency}ms avg, ${throughput} trades/sec"
echo "Test 2 (Coldpath Backdated): ${avg_latency}ms avg"
echo "Test 3 (Lifecycle): ${avg_latency}ms avg"
echo ""
echo "All performance tests completed!"
