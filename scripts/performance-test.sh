#!/bin/bash

# Performance Test Script for Position Management Service
# Tests various scenarios: hotpath, coldpath, batch processing

set -e

BASE_URL="${BASE_URL:-http://localhost:8081}"
POS_KEY_PREFIX="PERF-$(date +%s)"

echo "=========================================="
echo "Position Management Service Performance Test"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo "Position Key Prefix: $POS_KEY_PREFIX"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Test 1: Hotpath Performance - Sequential Current-Dated Trades
echo -e "${GREEN}=== Test 1: Hotpath Performance - Sequential Current-Dated Trades ===${NC}"
POS_KEY_1="${POS_KEY_PREFIX}-HOTPATH"
TRADE_COUNT=50

start_time=$(date +%s%N)
for i in $(seq 0 $((TRADE_COUNT - 1))); do
    trade_id="HOTPATH-T-$i"
    trade_type=$([ $i -eq 0 ] && echo "NEW_TRADE" || ([ $((i % 2)) -eq 0 ] && echo "INCREASE" || echo "DECREASE"))
    qty=$([ $((i % 2)) -eq 0 ] && echo "100" || echo "50")
    price=$(echo "50.00 + $i * 0.1" | bc)
    effective_date=$(date -v+${i}d +%Y-%m-%d 2>/dev/null || date -d "+${i} days" +%Y-%m-%d)
    
    response=$(curl -s -X POST "$BASE_URL/api/trades" \
        -H "Content-Type: application/json" \
        -d "{
            \"tradeId\": \"$trade_id\",
            \"positionKey\": \"$POS_KEY_1\",
            \"tradeType\": \"$trade_type\",
            \"quantity\": $qty,
            \"price\": $price,
            \"effectiveDate\": \"$effective_date\",
            \"contractId\": \"PERF-CONTRACT-001\",
            \"correlationId\": \"CORR-PERF-001\",
            \"causationId\": \"CAUS-PERF-001\",
            \"userId\": \"PERF-USER\"
        }")
    
    if [ $((i % 10)) -eq 0 ]; then
        echo "  Processed trade $i"
    fi
done
end_time=$(date +%s%N)
duration_ms=$(( (end_time - start_time) / 1000000 ))
avg_latency=$(echo "scale=2; $duration_ms / $TRADE_COUNT" | bc)

echo "  Total trades: $TRADE_COUNT"
echo "  Total time: ${duration_ms}ms"
echo "  Average latency: ${avg_latency}ms"
echo "  Throughput: $(echo "scale=2; $TRADE_COUNT * 1000 / $duration_ms" | bc) trades/second"
echo ""

# Test 2: Coldpath Performance - Backdated Trade Recalculation
echo -e "${GREEN}=== Test 2: Coldpath Performance - Backdated Trade Recalculation ===${NC}"
POS_KEY_2="${POS_KEY_PREFIX}-COLDPATH"

# First create position with forward-dated trades
echo "  Creating position with 10 forward-dated trades..."
for i in $(seq 0 9); do
    trade_id="FORWARD-T-$i"
    effective_date=$(date -v-10d -v+${i}d +%Y-%m-%d 2>/dev/null || date -d "-10 days +${i} days" +%Y-%m-%d)
    
    curl -s -X POST "$BASE_URL/api/trades" \
        -H "Content-Type: application/json" \
        -d "{
            \"tradeId\": \"$trade_id\",
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
for i in $(seq 0 4); do
    trade_id="BACKDATED-T-$i"
    effective_date=$(date -v-10d -v+$((i * 2))d +%Y-%m-%d 2>/dev/null || date -d "-10 days +$((i * 2)) days" +%Y-%m-%d)
    
    response=$(curl -s -X POST "$BASE_URL/api/diagnostics/recalculate" \
        -H "Content-Type: application/json" \
        -d "{
            \"tradeId\": \"$trade_id\",
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
        }")
    
    echo "    Processed backdated trade $i"
done
end_time=$(date +%s%N)
duration_ms=$(( (end_time - start_time) / 1000000 ))
avg_latency=$(echo "scale=2; $duration_ms / 5" | bc)

echo "  Total backdated trades: 5"
echo "  Total time: ${duration_ms}ms"
echo "  Average latency: ${avg_latency}ms"
echo ""

# Test 3: Batch Processing Performance
echo -e "${GREEN}=== Test 3: Batch Processing Performance ===${NC}"
POS_KEY_3="${POS_KEY_PREFIX}-BATCH"
BATCH_SIZE=30

start_time=$(date +%s%N)
for i in $(seq 0 $((BATCH_SIZE - 1))); do
    trade_id="BATCH-T-$i"
    effective_date=$(date -v+${i}d +%Y-%m-%d 2>/dev/null || date -d "+${i} days" +%Y-%m-%d)
    
    curl -s -X POST "$BASE_URL/api/trades" \
        -H "Content-Type: application/json" \
        -d "{
            \"tradeId\": \"$trade_id\",
            \"positionKey\": \"$POS_KEY_3-BATCH-$i\",
            \"tradeType\": \"NEW_TRADE\",
            \"quantity\": 100,
            \"price\": 50.00,
            \"effectiveDate\": \"$effective_date\",
            \"contractId\": \"PERF-CONTRACT-003\",
            \"correlationId\": \"CORR-BATCH-$i\",
            \"causationId\": \"CAUS-BATCH-$i\",
            \"userId\": \"PERF-USER\"
        }" > /dev/null &
done
wait
end_time=$(date +%s%N)
duration_ms=$(( (end_time - start_time) / 1000000 ))
throughput=$(echo "scale=2; $BATCH_SIZE * 1000 / $duration_ms" | bc)

echo "  Batch size: $BATCH_SIZE"
echo "  Total time: ${duration_ms}ms"
echo "  Throughput: ${throughput} trades/second"
echo ""

# Test 4: Position Lifecycle Performance
echo -e "${GREEN}=== Test 4: Position Lifecycle Performance ===${NC}"
POS_KEY_4="${POS_KEY_PREFIX}-LIFECYCLE"
base_date=$(date +%Y-%m-%d)

echo "  Creating position..."
start_time=$(date +%s%N)
curl -s -X POST "$BASE_URL/api/trades" \
    -H "Content-Type: application/json" \
    -d "{
        \"tradeId\": \"LIFECYCLE-NEW\",
        \"positionKey\": \"$POS_KEY_4\",
        \"tradeType\": \"NEW_TRADE\",
        \"quantity\": 1000,
        \"price\": 50.00,
        \"effectiveDate\": \"$base_date\",
        \"contractId\": \"PERF-CONTRACT-004\",
        \"correlationId\": \"CORR-LIFECYCLE\",
        \"causationId\": \"CAUS-LIFECYCLE\",
        \"userId\": \"PERF-USER\"
    }" > /dev/null
latency_1=$(( ($(date +%s%N) - start_time) / 1000000 ))

echo "  Adding to position..."
start_time=$(date +%s%N)
date_1=$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d "+1 day" +%Y-%m-%d)
curl -s -X POST "$BASE_URL/api/trades" \
    -H "Content-Type: application/json" \
    -d "{
        \"tradeId\": \"LIFECYCLE-INCREASE\",
        \"positionKey\": \"$POS_KEY_4\",
        \"tradeType\": \"INCREASE\",
        \"quantity\": 500,
        \"price\": 55.00,
        \"effectiveDate\": \"$date_1\",
        \"contractId\": \"PERF-CONTRACT-004\",
        \"correlationId\": \"CORR-LIFECYCLE\",
        \"causationId\": \"CAUS-LIFECYCLE\",
        \"userId\": \"PERF-USER\"
    }" > /dev/null
latency_2=$(( ($(date +%s%N) - start_time) / 1000000 ))

echo "  Partial close..."
start_time=$(date +%s%N)
date_2=$(date -v+2d +%Y-%m-%d 2>/dev/null || date -d "+2 days" +%Y-%m-%d)
curl -s -X POST "$BASE_URL/api/trades" \
    -H "Content-Type: application/json" \
    -d "{
        \"tradeId\": \"LIFECYCLE-PARTIAL\",
        \"positionKey\": \"$POS_KEY_4\",
        \"tradeType\": \"DECREASE\",
        \"quantity\": 500,
        \"price\": 60.00,
        \"effectiveDate\": \"$date_2\",
        \"contractId\": \"PERF-CONTRACT-004\",
        \"correlationId\": \"CORR-LIFECYCLE\",
        \"causationId\": \"CAUS-LIFECYCLE\",
        \"userId\": \"PERF-USER\"
    }" > /dev/null
latency_3=$(( ($(date +%s%N) - start_time) / 1000000 ))

echo "  Full close..."
start_time=$(date +%s%N)
date_3=$(date -v+3d +%Y-%m-%d 2>/dev/null || date -d "+3 days" +%Y-%m-%d)
curl -s -X POST "$BASE_URL/api/trades" \
    -H "Content-Type: application/json" \
    -d "{
        \"tradeId\": \"LIFECYCLE-FULL\",
        \"positionKey\": \"$POS_KEY_4\",
        \"tradeType\": \"DECREASE\",
        \"quantity\": 1000,
        \"price\": 65.00,
        \"effectiveDate\": \"$date_3\",
        \"contractId\": \"PERF-CONTRACT-004\",
        \"correlationId\": \"CORR-LIFECYCLE\",
        \"causationId\": \"CAUS-LIFECYCLE\",
        \"userId\": \"PERF-USER\"
    }" > /dev/null
latency_4=$(( ($(date +%s%N) - start_time) / 1000000 ))

echo "  Reopening..."
start_time=$(date +%s%N)
date_4=$(date -v+4d +%Y-%m-%d 2>/dev/null || date -d "+4 days" +%Y-%m-%d)
curl -s -X POST "$BASE_URL/api/trades" \
    -H "Content-Type: application/json" \
    -d "{
        \"tradeId\": \"LIFECYCLE-REOPEN\",
        \"positionKey\": \"$POS_KEY_4\",
        \"tradeType\": \"NEW_TRADE\",
        \"quantity\": 200,
        \"price\": 70.00,
        \"effectiveDate\": \"$date_4\",
        \"contractId\": \"PERF-CONTRACT-004\",
        \"correlationId\": \"CORR-LIFECYCLE\",
        \"causationId\": \"CAUS-LIFECYCLE\",
        \"userId\": \"PERF-USER\"
    }" > /dev/null
latency_5=$(( ($(date +%s%N) - start_time) / 1000000 ))

avg_latency=$(echo "scale=2; ($latency_1 + $latency_2 + $latency_3 + $latency_4 + $latency_5) / 5" | bc)
echo "  Operations: Create, Add, Partial Close, Full Close, Reopen"
echo "  Latencies: ${latency_1}ms, ${latency_2}ms, ${latency_3}ms, ${latency_4}ms, ${latency_5}ms"
echo "  Average latency: ${avg_latency}ms"
echo ""

# Summary
echo -e "${GREEN}=========================================="
echo "Performance Test Summary"
echo "==========================================${NC}"
echo "Test 1 (Hotpath Sequential): ${avg_latency}ms avg"
echo "Test 2 (Coldpath Backdated): ${avg_latency}ms avg"
echo "Test 3 (Batch Processing): ${throughput} trades/sec"
echo "Test 4 (Lifecycle): ${avg_latency}ms avg"
echo ""
echo "All performance tests completed!"
