#!/bin/bash

# Load Test Script for Position Management Service
# Uses JSON files similar to e2e test approach
# Tests: Ramp-up, Sustained Load, Spike Test

set -e

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_JSONS_DIR="${SCRIPT_DIR}/test_jsons"

# Helper function to load and process JSON file (replace date placeholders)
load_json() {
    local json_file="$1"
    local json_content=$(cat "${TEST_JSONS_DIR}/${json_file}")
    
    # Replace date placeholders
    local current_date=$(date +%Y-%m-%d)
    local forward_date=$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d "+1 day" +%Y-%m-%d 2>/dev/null || echo "$current_date")
    local backdate=$(date -v-1d +%Y-%m-%d 2>/dev/null || date -d "-1 day" +%Y-%m-%d 2>/dev/null || echo "$current_date")
    
    echo "$json_content" | sed "s/CURRENT_DATE/$current_date/g" | sed "s/FORWARD_DATE/$forward_date/g" | sed "s/BACKDATE/$backdate/g"
}

# Helper function to update trade ID in JSON
update_trade_id() {
    local json_content="$1"
    local new_trade_id="$2"
    echo "$json_content" | sed "s/\"tradeId\": \"[^\"]*\"/\"tradeId\": \"$new_trade_id\"/"
}

# Helper function to update account in JSON
update_account() {
    local json_content="$1"
    local new_account="$2"
    echo "$json_content" | sed "s/\"account\": \"[^\"]*\"/\"account\": \"$new_account\"/"
}

BASE_URL="${BASE_URL:-http://localhost:8080}"
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

# Function to create a trade using JSON file
create_trade() {
    local trade_id=$1
    local account=$2
    local json_template=$3
    local iteration=$4
    
    # Load base JSON
    local json_content=$(load_json "$json_template")
    
    # Update trade ID and account
    json_content=$(update_trade_id "$json_content" "$trade_id")
    json_content=$(update_account "$json_content" "$account")
    
    # Update quantity/price if iteration is provided (for variety)
    if [ -n "$iteration" ]; then
        # Vary quantity slightly based on iteration (handle negative quantities for decrease trades)
        local base_qty=$(echo "$json_content" | grep -o '"quantity": -\{0,1\}[0-9.]*' | cut -d: -f2 | tr -d ' ' || echo "100")
        local is_negative=false
        if [[ "$base_qty" == -* ]]; then
            is_negative=true
            base_qty=${base_qty#-}  # Remove negative sign
        fi
        local qty_abs=$(echo "$base_qty" | tr -d '-' | cut -d. -f1)
        local new_qty=$((qty_abs + (iteration % 50)))
        if [ "$is_negative" = true ]; then
            new_qty=$((0 - new_qty))  # Make it negative
        fi
        # Use sed with proper pattern to match negative or positive numbers
        json_content=$(echo "$json_content" | sed "s/\"quantity\": -\{0,1\}[0-9.]*/\"quantity\": $new_qty/")
        
        # Vary price slightly
        local base_price=$(echo "$json_content" | grep -o '"price": [0-9.]*' | cut -d: -f2 | tr -d ' ' || echo "50.0")
        local price_delta=$(echo "scale=2; $iteration * 0.1" | bc 2>/dev/null || echo "0.1")
        local new_price=$(echo "scale=2; $base_price + $price_delta" | bc 2>/dev/null || echo "$base_price")
        json_content=$(echo "$json_content" | sed "s/\"price\": [0-9.]*/\"price\": $new_price/")
    fi
    
    response=$(curl -s -w "\nHTTP_CODE:%{http_code}\nTIME_TOTAL:%{time_total}" -X POST "$BASE_URL/api/trades" \
        -H "Content-Type: application/json" \
        -d "$json_content" 2>&1)
    
    http_code=$(echo "$response" | grep "HTTP_CODE:" | cut -d: -f2 | tr -d ' ' || echo "000")
    time_total=$(echo "$response" | grep "TIME_TOTAL:" | cut -d: -f2 | tr -d ' ' || echo "0")
    
    # Convert time_total to milliseconds (curl returns seconds)
    time_total_ms=$(echo "scale=3; $time_total * 1000" | bc 2>/dev/null || echo "$time_total")
    
    # Extract error message if any (for debugging)
    error_msg=""
    if [ "$http_code" != "200" ] && [ "$http_code" != "201" ]; then
        error_msg=$(echo "$response" | grep -v "HTTP_CODE:" | grep -v "TIME_TOTAL:" | head -1 | cut -c1-50)
    fi
    
    echo "$http_code|$time_total_ms|$error_msg"
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
            # Use unique account per user to avoid position conflicts
            local account="LOAD-ACC-${test_name}-U${user}"
            local position_key="${position_key_base}-U${user}"
            
            for req in $(seq 1 $requests_per_user); do
                local trade_id="LOAD-${test_name}-U${user}-R${req}"
                
                # Smart trade sequencing: ensure initial trades come before decreases
                # Sequence: initial -> increase -> decrease -> new (for different account)
                local json_template=""
                case $((req % 4)) in
                    1)
                        # First request: always initial trade to establish position
                        json_template="load_initial_trade.json"
                        ;;
                    2)
                        # Second request: increase existing position
                        json_template="load_increase_trade.json"
                        ;;
                    3)
                        # Third request: decrease position (safe because we have initial + increase)
                        json_template="load_decrease_trade.json"
                        ;;
                    0)
                        # Fourth request: new trade with different account/instrument
                        json_template="load_new_trade.json"
                        ;;
                esac
                
                # Fallback to initial_trade.json if template not found
                if [ ! -f "${TEST_JSONS_DIR}/${json_template}" ]; then
                    json_template="initial_trade.json"
                fi
                
                result=$(create_trade "$trade_id" "$account" "$json_template" "$req")
                echo "$result" >> "$results_file"
                
                # Small delay between requests to avoid overwhelming
                sleep 0.01
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
    
    # Analyze results
    if [ ! -s "$results_file" ]; then
        local total_requests=0
        local success_count=0
        local error_count=0
        local error_400=0
        local error_500=0
    else
        local total_requests=$(awk 'END {print NR}' "$results_file" 2>/dev/null || echo "0")
        local success_count=$(grep -c "^200|" "$results_file" 2>/dev/null || echo "0")
        local success_201=$(grep -c "^201|" "$results_file" 2>/dev/null || echo "0")
        local error_400=$(grep -c "^400|" "$results_file" 2>/dev/null || echo "0")
        local error_500=$(grep -c "^500|" "$results_file" 2>/dev/null || echo "0")
        
        total_requests=$(echo "$total_requests" | tr -d '\n\r' | grep -E '^[0-9]+$' || echo "0")
        success_count=$(echo "$success_count" | tr -d '\n\r' | grep -E '^[0-9]+$' || echo "0")
        success_201=$(echo "$success_201" | tr -d '\n\r' | grep -E '^[0-9]+$' || echo "0")
        error_400=$(echo "$error_400" | tr -d '\n\r' | grep -E '^[0-9]+$' || echo "0")
        error_500=$(echo "$error_500" | tr -d '\n\r' | grep -E '^[0-9]+$' || echo "0")
        
        success_count=$((success_count + success_201))
        local error_count=$((total_requests - success_count))
        
        # Show sample error messages
        if [ $error_count -gt 0 ]; then
            echo -e "${YELLOW}Sample errors:${NC}"
            grep -E "^(400|500)\|" "$results_file" 2>/dev/null | head -3 | while IFS='|' read -r code time msg; do
                echo "  HTTP $code: ${msg:0:60}"
            done
        fi
    fi
    
    # Extract times for successful requests (200 or 201)
    local times=$(grep -E "^(200|201)" "$results_file" 2>/dev/null | cut -d'|' -f2 | grep -E "^[0-9]+\.?[0-9]*$" || echo "")
    local avg_time=0
    local min_time=0
    local max_time=0
    local p50=0
    local p95=0
    local p99=0
    
    if [ -n "$times" ] && [ "$times" != "" ]; then
        # Filter out empty lines and calculate stats
        local valid_times=$(echo "$times" | grep -v "^$" | grep -E "^[0-9]+\.?[0-9]*$")
        if [ -n "$valid_times" ]; then
            avg_time=$(echo "$valid_times" | awk '{sum+=$1; count++} END {if(count>0) print sum/count; else print 0}')
            min_time=$(echo "$valid_times" | awk 'BEGIN{min=999999} {if($1<min && $1>0) min=$1} END {if(min==999999) print 0; else print min}')
            max_time=$(echo "$valid_times" | awk 'BEGIN{max=0} {if($1>max) max=$1} END {print max}')
        
            # Calculate percentiles
            local sorted_times=$(echo "$valid_times" | sort -n)
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
    fi
    
    local throughput=0
    local error_rate=0
    if [ "$duration_ms" -gt 0 ]; then
        throughput=$(echo "scale=2; $total_requests * 1000 / $duration_ms" | bc 2>/dev/null || echo "0")
    fi
    if [ "$total_requests" -gt 0 ]; then
        error_rate=$(echo "scale=2; $error_count * 100 / $total_requests" | bc 2>/dev/null || echo "0")
    fi
    
    echo -e "${GREEN}Results:${NC}"
    echo "  Total requests: $total_requests"
    echo "  Successful: $success_count"
    echo "  Errors: $error_count (${error_rate}%)"
    if [ "$error_400" -gt 0 ] || [ "$error_500" -gt 0 ]; then
        echo "    - 400 (Bad Request): $error_400"
        echo "    - 500 (Server Error): $error_500"
    fi
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
    
    # Store error rate for caller
    echo "$error_rate" > "/tmp/load_test_error_rate_$$"
}

# Test 1: Ramp-up Load Test
echo -e "${YELLOW}=== Test 1: Ramp-up Load Test ===${NC}"
echo "Gradually increasing load from 1 to $CONCURRENT_USERS users over $RAMP_UP_DURATION seconds"
echo ""

ramp_step=$((CONCURRENT_USERS / RAMP_UP_STEPS))
for step in $(seq 1 $RAMP_UP_STEPS); do
    current_users=$((step * ramp_step))
    [ $current_users -eq 0 ] && current_users=1
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
    remaining=$((end_time - $(date +%s)))
    echo -e "${BLUE}Iteration $iteration (${remaining}s remaining)${NC}"
    run_concurrent_load $CONCURRENT_USERS $REQUESTS_PER_USER "SUSTAINED-I${iteration}"
    error_rate=$(cat "/tmp/load_test_error_rate_$$" 2>/dev/null || echo "0")
    
    if [ $remaining -gt 5 ]; then
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

# Cleanup
rm -f "/tmp/load_test_error_rate_$$"
