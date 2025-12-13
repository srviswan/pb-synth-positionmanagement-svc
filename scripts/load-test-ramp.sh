#!/bin/bash

# Ramp-up Load Test Script
# Gradually increases load to test system behavior under increasing stress

set -e

BASE_URL="${BASE_URL:-http://localhost:8081}"
MAX_CONCURRENT="${MAX_CONCURRENT:-100}"
RAMP_STEPS="${RAMP_STEPS:-10}"
REQUESTS_PER_STEP="${REQUESTS_PER_STEP:-50}"

echo "=========================================="
echo "Ramp-up Load Test"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo "Max Concurrent: $MAX_CONCURRENT"
echo "Ramp Steps: $RAMP_STEPS"
echo "Requests per Step: $REQUESTS_PER_STEP"
echo ""

POS_KEY="RAMP-TEST-$(date +%s)"
base_date=$(date +%Y-%m-%d)

# Function to run load test for a given concurrency level
run_load_step() {
    local concurrent=$1
    local step=$2
    local requests=$3
    
    echo "Step $step: $concurrent concurrent users, $requests requests each"
    
    local start_time=$(date +%s%N)
    local pids=()
    local results_file=$(mktemp)
    
    # Launch concurrent requests
    for user in $(seq 1 $concurrent); do
        (
            local position_key="${POS_KEY}-U${user}"
            for req in $(seq 1 $requests); do
                local trade_id="RAMP-S${step}-U${user}-R${req}"
                local trade_type=$([ $req -eq 1 ] && echo "NEW_TRADE" || ([ $((req % 2)) -eq 0 ] && echo "INCREASE" || echo "DECREASE"))
                local qty=$([ $((req % 2)) -eq 0 ] && echo "100" || echo "50")
                local price=$(python3 -c "print(50.00 + $req * 0.1)")
                local effective_date=$(date -v+${req}d +%Y-%m-%d 2>/dev/null || date -d "+${req} days" +%Y-%m-%d 2>/dev/null || echo "$base_date")
                
                response=$(curl -s -w "\n%{http_code}\n%{time_total}" -X POST "$BASE_URL/api/trades" \
                    -H "Content-Type: application/json" \
                    -d "{
                        \"tradeId\": \"$trade_id\",
                        \"positionKey\": \"$position_key\",
                        \"tradeType\": \"$trade_type\",
                        \"quantity\": $qty,
                        \"price\": $price,
                        \"effectiveDate\": \"$effective_date\",
                        \"contractId\": \"RAMP-CONTRACT-001\",
                        \"correlationId\": \"CORR-RAMP\",
                        \"causationId\": \"CAUS-RAMP\",
                        \"userId\": \"RAMP-USER\"
                    }" 2>&1)
                
                http_code=$(echo "$response" | tail -2 | head -1)
                time_total=$(echo "$response" | tail -1)
                echo "$http_code|$time_total" >> "$results_file"
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
    local total_requests=$(wc -l < "$results_file" | tr -d ' ')
    local success_count=$(grep -c "^200|" "$results_file" || echo "0")
    local error_count=$((total_requests - success_count))
    
    local times=$(grep "^200|" "$results_file" | cut -d'|' -f2)
    local avg_time=$(echo "$times" | awk '{sum+=$1; count++} END {if(count>0) print sum/count; else print 0}')
    local max_time=$(echo "$times" | awk 'BEGIN{max=0} {if($1>max) max=$1} END {print max}')
    
    local sorted_times=$(echo "$times" | sort -n)
    local count=$(echo "$sorted_times" | wc -l | tr -d ' ')
    local p95_idx=$((count * 95 / 100))
    local p99_idx=$((count * 99 / 100))
    
    local p95=$(echo "$sorted_times" | sed -n "${p95_idx}p" || echo "0")
    local p99=$(echo "$sorted_times" | sed -n "${p99_idx}p" || echo "0")
    
    local throughput=$(python3 -c "print(round($success_count * 1000 / $duration_ms, 2))" 2>/dev/null || echo "0")
    local error_rate=$(python3 -c "print(round($error_count * 100 / $total_requests, 2))" 2>/dev/null || echo "0")
    
    echo "  Results:"
    echo "    Requests: $total_requests (Success: $success_count, Errors: $error_count, ${error_rate}%)"
    echo "    Duration: ${duration_ms}ms"
    echo "    Throughput: ${throughput} req/s"
    echo "    Avg Latency: $(printf "%.2f" $avg_time)ms"
    echo "    P95 Latency: $(printf "%.2f" $p95)ms"
    echo "    P99 Latency: $(printf "%.2f" $p99)ms"
    echo "    Max Latency: $(printf "%.2f" $max_time)ms"
    echo ""
    
    rm -f "$results_file"
    
    # Return error rate
    echo "$error_rate"
}

# Ramp up load
step_size=$((MAX_CONCURRENT / RAMP_STEPS))
for step in $(seq 1 $RAMP_STEPS); do
    concurrent=$((step * step_size))
    error_rate=$(run_load_step $concurrent $step $REQUESTS_PER_STEP)
    
    # If error rate is too high, stop
    error_rate_num=$(echo "$error_rate" | sed 's/[^0-9.]//g')
    if [ -n "$error_rate_num" ] && (( $(echo "$error_rate_num > 5" | bc -l 2>/dev/null || echo "0") )); then
        echo "⚠️  Error rate exceeded 5% at $concurrent concurrent users. Stopping ramp-up."
        break
    fi
    
    # Brief pause between steps
    sleep 2
done

echo "Ramp-up load test completed!"
