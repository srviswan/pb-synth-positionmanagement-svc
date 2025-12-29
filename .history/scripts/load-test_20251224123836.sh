#!/bin/bash

# Load Test Script for Position Management Service
# Tests: Ramp-up, Sustained Load, Spike Test

set -e

BASE_URL="${BASE_URL:-http://localhost:8081}"
POS_KEY_PREFIX="LOAD-$(date +%s)"

# Test parameters
RAMP_UP_STEPS="${RAMP_UP_STEPS:-5}"
RAMP_UP_DURATION="${RAMP_UP_DURATION:-30}"
SUSTAINED_DURATION="${SUSTAINED_DURATION:-60}"
SPIKE_DURATION="${SPIKE_DURATION:-10}"
CONCURRENT_USERS="${CONCURRENT_USERS:-50}"
REQUESTS_PER_USER="${REQUESTS_PER_USER:-10}"

echo "=========================================="
echo "Position Management Service Load Test"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo "Position Key Prefix: $POS_KEY_PREFIX"
echo "Concurrent Users: $CONCURRENT_USERS"
echo "Requests per User: $REQUESTS_PER_USER"
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

# Function to create a trade
create_trade() {
    local trade_id=$1
    local position_key=$2
    local trade_type=$3
    local qty=$4
    local price=$5
    local effective_date=$6
    
    # Extract account, instrument, currency from position_key or use defaults
    local account=$(echo "$position_key" | cut -d'-' -f1 || echo "LOAD-ACC")
    local instrument="AAPL"
    local currency="USD"
    
    response=$(curl -s -w "\nHTTP_CODE:%{http_code}\nTIME_TOTAL:%{time_total}" -X POST "$BASE_URL/api/trades" \
        -H "Content-Type: application/json" \
        -d "{
            \"tradeId\": \"$trade_id\",
            \"account\": \"$account\",
            \"instrument\": \"$instrument\",
            \"currency\": \"$currency\",
            \"tradeType\": \"$trade_type\",
            \"quantity\": $qty,
            \"price\": $price,
            \"effectiveDate\": \"$effective_date\",
            \"contractId\": \"LOAD-CONTRACT-001\",
            \"correlationId\": \"CORR-LOAD-$trade_id\",
            \"causationId\": \"CAUS-LOAD-$trade_id\",
            \"userId\": \"LOAD-USER\"
        }" 2>&1)
    
    http_code=$(echo "$response" | grep "HTTP_CODE:" | cut -d: -f2 || echo "000")
    time_total=$(echo "$response" | grep "TIME_TOTAL:" | cut -d: -f2 || echo "0")
    
    echo "$http_code|$time_total"
}

# Function to run concurrent load
run_concurrent_load() {
    local num_users=$1
    local requests_per_user=$2
    local test_name=$3
    local position_key_base="${POS_KEY_PREFIX}-${test_name}"
    
    echo -e "${BLUE}Running $test_name: $num_users concurrent users, $requests_per_user requests each${NC}"
    
    local start_time=$(date +%s%N)
    local pids=()
    local results_file=$(mktemp)
    
    # Launch concurrent requests
    for user in $(seq 1 $num_users); do
        (
            local user_results=()
            local position_key="${position_key_base}-U${user}"
            local base_date=$(date +%Y-%m-%d)
            
            for req in $(seq 1 $requests_per_user); do
                local trade_id="LOAD-${test_name}-U${user}-R${req}"
                local trade_type=$([ $req -eq 1 ] && echo "NEW_TRADE" || ([ $((req % 2)) -eq 0 ] && echo "INCREASE" || echo "DECREASE"))
                local qty=$([ $((req % 2)) -eq 0 ] && echo "100" || echo "50")
                local price=$(python3 -c "print(round(50.00 + $req * 0.1, 2))" 2>/dev/null || echo "50.0")
                local effective_date=$(date -v+${req}d +%Y-%m-%d 2>/dev/null || date -d "+${req} days" +%Y-%m-%d 2>/dev/null || echo "$base_date")
                
                result=$(create_trade "$trade_id" "$position_key" "$trade_type" "$qty" "$price" "$effective_date")
                echo "$result" >> "$results_file"
            done
        ) &
        pids+=($!)
    done
    
    # Wait for all to complete
    for pid in "${pids[@]}"; do
        wait $pid
    done
    
    local end_time=$(date +%s%N)
    local duration_ms=$(( (end_time - start_time) / 1000000 ))
    
    # Analyze results - handle empty file case
    if [ ! -s "$results_file" ]; then
        local total_requests=0
        local success_count=0
        local error_count=0
    else
        # Get counts using awk to avoid whitespace issues
        local total_requests=$(awk 'END {print NR}' "$results_file" 2>/dev/null || echo "0")
        local success_count=$(grep -c "^200|" "$results_file" 2>/dev/null || echo "0")
        # Also count 201 (Created) as success
        local success_201=$(grep -c "^201|" "$results_file" 2>/dev/null || echo "0")
        
        # Strip newlines and ensure valid integers
        total_requests=$(echo "$total_requests" | tr -d '\n\r' | grep -E '^[0-9]+$' || echo "0")
        success_count=$(echo "$success_count" | tr -d '\n\r' | grep -E '^[0-9]+$' || echo "0")
        success_201=$(echo "$success_201" | tr -d '\n\r' | grep -E '^[0-9]+$' || echo "0")
        
        success_count=$((success_count + success_201))
        
        local error_count=$((total_requests - success_count))
    fi
    
    local times=$(grep -E "^(200|201)|" "$results_file" 2>/dev/null | cut -d'|' -f2 || echo "")
    local avg_time=0
    local min_time=0
    local max_time=0
    local p50=0
    local p95=0
    local p99=0
    
    if [ -n "$times" ] && [ "$times" != "" ]; then
        avg_time=$(echo "$times" | awk '{sum+=$1; count++} END {if(count>0) print sum/count; else print 0}')
        min_time=$(echo "$times" | awk 'BEGIN{min=999} {if($1<min && $1>0) min=$1} END {if(min==999) print 0; else print min}')
        max_time=$(echo "$times" | awk 'BEGIN{max=0} {if($1>max) max=$1} END {print max}')
        
        # Calculate percentiles (simplified)
        local sorted_times=$(echo "$times" | sort -n)
        local count=$(echo "$sorted_times" | wc -l | xargs || echo "0")
        count=$((count + 0))
        if [ $count -gt 0 ]; then
            local p50_idx=$((count * 50 / 100))
            local p95_idx=$((count * 95 / 100))
            local p99_idx=$((count * 99 / 100))
            
            [ $p50_idx -eq 0 ] && p50_idx=1
            [ $p95_idx -eq 0 ] && p95_idx=1
            [ $p99_idx -eq 0 ] && p99_idx=1
            
            p50=$(echo "$sorted_times" | sed -n "${p50_idx}p" || echo "0")
            p95=$(echo "$sorted_times" | sed -n "${p95_idx}p" || echo "0")
            p99=$(echo "$sorted_times" | sed -n "${p99_idx}p" || echo "0")
        fi
    fi
    
    local throughput=$(python3 -c "print(round($total_requests * 1000 / $duration_ms, 2))" 2>/dev/null || echo "0")
    local error_rate=$(python3 -c "print(round($error_count * 100 / $total_requests, 2))" 2>/dev/null || echo "0")
    
    echo -e "${GREEN}Results:${NC}"
    echo "  Total requests: $total_requests"
    echo "  Successful: $success_count"
    echo "  Errors: $error_count (${error_rate}%)"
    echo "  Total time: ${duration_ms}ms"
    echo "  Throughput: ${throughput} requests/second"
    echo "  Average latency: $(printf "%.2f" $avg_time)ms"
    echo "  Min latency: $(printf "%.2f" $min_time)ms"
    echo "  Max latency: $(printf "%.2f" $max_time)ms"
    echo "  P50 latency: $(printf "%.2f" $p50)ms"
    echo "  P95 latency: $(printf "%.2f" $p95)ms"
    echo "  P99 latency: $(printf "%.2f" $p99)ms"
    echo ""
    
    rm -f "$results_file"
    
    # Store error rate in a temp file for caller to read
    echo "$error_rate" > "/tmp/load_test_error_rate_$$"
}

# Test 1: Ramp-up Load Test
echo -e "${YELLOW}=== Test 1: Ramp-up Load Test ===${NC}"
echo "Gradually increasing load from 1 to $CONCURRENT_USERS users over $RAMP_UP_DURATION seconds"
echo ""

ramp_step=$((CONCURRENT_USERS / RAMP_UP_STEPS))
for step in $(seq 1 $RAMP_UP_STEPS); do
    current_users=$((step * ramp_step))
    echo -e "${BLUE}Step $step/$RAMP_UP_STEPS: $current_users concurrent users${NC}"
    run_concurrent_load $current_users 5 "RAMP-STEP${step}"
    error_rate=$(cat "/tmp/load_test_error_rate_$$" 2>/dev/null || echo "0")
    sleep $((RAMP_UP_DURATION / RAMP_UP_STEPS))
done

echo ""

# Test 2: Sustained Load Test
echo -e "${YELLOW}=== Test 2: Sustained Load Test ===${NC}"
echo "Maintaining $CONCURRENT_USERS concurrent users for $SUSTAINED_DURATION seconds"
echo ""

start_time=$(date +%s)
end_time=$((start_time + SUSTAINED_DURATION))
iteration=0

while [ $(date +%s) -lt $end_time ]; do
    iteration=$((iteration + 1))
    echo -e "${BLUE}Iteration $iteration (${SUSTAINED_DURATION}s total)${NC}"
    run_concurrent_load $CONCURRENT_USERS $REQUESTS_PER_USER "SUSTAINED-I${iteration}"
    error_rate=$(cat "/tmp/load_test_error_rate_$$" 2>/dev/null || echo "0")
    
    remaining=$((end_time - $(date +%s)))
    if [ $remaining -gt 0 ]; then
        sleep 5
    fi
done

echo ""

# Test 3: Spike Test
echo -e "${YELLOW}=== Test 3: Spike Test ===${NC}"
echo "Sudden burst of $((CONCURRENT_USERS * 2)) concurrent users for $SPIKE_DURATION seconds"
echo ""

spike_users=$((CONCURRENT_USERS * 2))
run_concurrent_load $spike_users $REQUESTS_PER_USER "SPIKE"
error_rate=$(cat "/tmp/load_test_error_rate_$$" 2>/dev/null || echo "0")

echo ""

# Summary
echo -e "${GREEN}=========================================="
echo "Load Test Summary"
echo "==========================================${NC}"
echo "All load tests completed!"
echo ""
echo "Monitor the application for:"
echo "  - CPU usage"
echo "  - Memory usage"
echo "  - Database connection pool"
echo "  - Response times"
echo "  - Error rates"
echo ""
