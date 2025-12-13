# Load Testing Guide

## Overview

Load testing validates the Position Management Service's ability to handle high volumes of concurrent requests and sustained load. This guide covers different load test scenarios and how to run them.

## Load Test Scripts

### 1. Simple Load Test (`load-test-simple.sh`)

**Purpose**: Quick load test using Apache Bench (ab), wrk, or curl fallback.

**Usage**:
```bash
# Default: 1000 requests, 50 concurrent
./scripts/load-test-simple.sh

# Custom parameters
TOTAL_REQUESTS=2000 CONCURRENT_REQUESTS=100 ./scripts/load-test-simple.sh
```

**Output Metrics**:
- Total requests
- Successful/Error counts
- Throughput (requests/second)
- Latency (average, min, max, P50, P95, P99)
- Error rate

### 2. Comprehensive Load Test (`load-test.sh`)

**Purpose**: Multi-scenario load test including ramp-up, sustained load, and spike tests.

**Usage**:
```bash
# Default parameters
./scripts/load-test.sh

# Custom parameters
CONCURRENT_USERS=100 REQUESTS_PER_USER=20 ./scripts/load-test.sh
```

**Test Scenarios**:
1. **Ramp-up Test**: Gradually increases load from 1 to max users
2. **Sustained Load Test**: Maintains constant high load for specified duration
3. **Spike Test**: Sudden burst of 2x normal load

**Parameters**:
- `RAMP_UP_STEPS`: Number of ramp-up steps (default: 5)
- `RAMP_UP_DURATION`: Total ramp-up duration in seconds (default: 30)
- `SUSTAINED_DURATION`: Sustained load duration in seconds (default: 60)
- `SPIKE_DURATION`: Spike test duration in seconds (default: 10)
- `CONCURRENT_USERS`: Number of concurrent users (default: 50)
- `REQUESTS_PER_USER`: Requests per user (default: 10)

### 3. Ramp-up Load Test (`load-test-ramp.sh`)

**Purpose**: Gradually increases load to identify breaking points and system behavior under increasing stress.

**Usage**:
```bash
# Default: Ramp to 100 concurrent users in 10 steps
./scripts/load-test-ramp.sh

# Custom parameters
MAX_CONCURRENT=200 RAMP_STEPS=10 REQUESTS_PER_STEP=20 ./scripts/load-test-ramp.sh
```

**Features**:
- Automatically stops if error rate exceeds 5%
- Shows metrics at each step
- Identifies maximum sustainable load

## Load Test Scenarios

### Scenario 1: Light Load
**Purpose**: Baseline performance measurement

```bash
TOTAL_REQUESTS=100 CONCURRENT_REQUESTS=10 ./scripts/load-test-simple.sh
```

**Expected Results**:
- Error rate: < 0.1%
- Average latency: < 50ms
- P99 latency: < 100ms

### Scenario 2: Normal Load
**Purpose**: Typical production load

```bash
TOTAL_REQUESTS=1000 CONCURRENT_REQUESTS=50 ./scripts/load-test-simple.sh
```

**Expected Results**:
- Error rate: < 0.5%
- Average latency: < 100ms
- P99 latency: < 200ms
- Throughput: > 20 requests/second

### Scenario 3: Heavy Load
**Purpose**: Peak load testing

```bash
TOTAL_REQUESTS=5000 CONCURRENT_REQUESTS=200 ./scripts/load-test-simple.sh
```

**Expected Results**:
- Error rate: < 1%
- Average latency: < 200ms
- P99 latency: < 500ms
- Throughput: > 50 requests/second

### Scenario 4: Stress Test
**Purpose**: Find breaking point

```bash
MAX_CONCURRENT=500 RAMP_STEPS=20 REQUESTS_PER_STEP=10 ./scripts/load-test-ramp.sh
```

**Expected Results**:
- System should handle load gracefully
- Error rate should remain low until breaking point
- Latency should increase gradually, not spike

### Scenario 5: Sustained Load
**Purpose**: Test system stability over time

```bash
SUSTAINED_DURATION=300 CONCURRENT_USERS=100 REQUESTS_PER_USER=50 ./scripts/load-test.sh
```

**Expected Results**:
- No memory leaks
- Consistent performance over time
- No connection pool exhaustion
- Stable error rate

### Scenario 6: Spike Test
**Purpose**: Test system response to sudden traffic spikes

```bash
SPIKE_DURATION=30 CONCURRENT_USERS=200 ./scripts/load-test.sh
```

**Expected Results**:
- System should handle spike without crashing
- Recovery time should be reasonable
- Error rate should return to normal after spike

## Load Test Results Interpretation

### Good Performance Indicators

✅ **Error Rate**: < 1% for normal load, < 5% for stress test
✅ **Latency**: Consistent, predictable latency distribution
✅ **Throughput**: Meets or exceeds target throughput
✅ **Resource Usage**: CPU, memory, connections within limits
✅ **Recovery**: System recovers quickly after load spike

### Performance Issues to Watch

⚠️ **High Error Rate**: 
- Check database connection pool
- Monitor database performance
- Check for resource exhaustion

⚠️ **Latency Spikes**:
- Database query performance
- Cache hit rates
- Network latency
- Garbage collection pauses

⚠️ **Degrading Performance**:
- Memory leaks
- Connection pool exhaustion
- Database lock contention
- Cache eviction issues

⚠️ **High P99 Relative to Average**:
- Tail latency issues
- Database slow queries
- Network congestion
- Resource contention

## Monitoring During Load Tests

### Application Metrics

Monitor these metrics during load tests:

1. **Response Times**:
   - Average, P50, P95, P99 latencies
   - Request processing time
   - Database query time

2. **Throughput**:
   - Requests per second
   - Successful requests per second
   - Failed requests per second

3. **Error Rates**:
   - HTTP error codes (4xx, 5xx)
   - Timeout errors
   - Database errors

4. **Resource Usage**:
   - CPU usage
   - Memory usage
   - Database connection pool usage
   - Thread pool usage

### Database Metrics

1. **Connection Pool**:
   - Active connections
   - Idle connections
   - Wait time for connections

2. **Query Performance**:
   - Slow queries
   - Lock contention
   - Transaction duration

3. **Database Load**:
   - CPU usage
   - I/O wait
   - Cache hit rates

### Infrastructure Metrics

1. **Network**:
   - Bandwidth usage
   - Latency
   - Packet loss

2. **Cache**:
   - Hit rate
   - Eviction rate
   - Memory usage

## Load Test Best Practices

### 1. Start Small
- Begin with light load
- Gradually increase
- Monitor at each step

### 2. Test Realistic Scenarios
- Use realistic data volumes
- Test actual user patterns
- Include various trade types

### 3. Monitor Everything
- Application metrics
- Database metrics
- Infrastructure metrics
- Error logs

### 4. Test Different Patterns
- Steady load
- Ramp-up
- Spike
- Sustained

### 5. Document Results
- Save test results
- Compare across runs
- Track improvements

### 6. Test Regularly
- Before releases
- After major changes
- Periodically in production

## Load Test Tools

### Apache Bench (ab)
```bash
# Install
brew install httpd  # macOS
apt-get install apache2-utils  # Linux

# Usage
ab -n 1000 -c 50 -p payload.json -T application/json http://localhost:8081/api/trades
```

### wrk
```bash
# Install
brew install wrk  # macOS
apt-get install wrk  # Linux

# Usage
wrk -t4 -c50 -d30s -s script.lua http://localhost:8081/api/trades
```

### JMeter
- GUI-based load testing tool
- Supports complex test scenarios
- Good for detailed analysis

### k6
- Modern load testing tool
- JavaScript-based scripts
- Good for CI/CD integration

## Example Load Test Results

### Light Load (100 requests, 10 concurrent)
```
Total requests: 100
Successful: 100
Errors: 0 (0.00%)
Throughput: 45.23 requests/second
Average latency: 22.10ms
P99 latency: 45.00ms
```

### Normal Load (1000 requests, 50 concurrent)
```
Total requests: 1000
Successful: 998
Errors: 2 (0.20%)
Throughput: 2401.43 requests/second
Average latency: 20.82ms
P99 latency: 85.00ms
```

### Heavy Load (5000 requests, 200 concurrent)
```
Total requests: 5000
Successful: 4985
Errors: 15 (0.30%)
Throughput: 1850.25 requests/second
Average latency: 108.10ms
P99 latency: 450.00ms
```

## Troubleshooting

### High Error Rate
1. Check database connection pool size
2. Monitor database performance
3. Check for resource limits
4. Review application logs

### High Latency
1. Check database query performance
2. Monitor cache hit rates
3. Check network latency
4. Review slow query logs

### Connection Pool Exhaustion
1. Increase pool size
2. Check for connection leaks
3. Monitor connection usage
4. Review connection timeout settings

### Memory Issues
1. Check for memory leaks
2. Monitor heap usage
3. Review GC logs
4. Adjust JVM settings

## Continuous Load Testing

For production environments, consider:

1. **Scheduled Load Tests**: Run load tests regularly
2. **Canary Testing**: Test new versions under load
3. **Chaos Engineering**: Test system resilience
4. **Performance Regression Testing**: Track performance over time

## Next Steps

After load testing:

1. **Analyze Results**: Identify bottlenecks
2. **Optimize**: Address performance issues
3. **Re-test**: Verify improvements
4. **Document**: Update performance baselines
5. **Monitor**: Set up production monitoring
