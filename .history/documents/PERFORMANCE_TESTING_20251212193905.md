# Performance Testing Guide

## Overview

Performance tests for the Position Management Service cover various scenarios:
- **Hotpath Performance**: Sequential current/forward-dated trades
- **Coldpath Performance**: Backdated trade recalculation
- **Batch Processing**: Concurrent trade processing
- **Position Lifecycle**: Create → Add → Close → Reopen cycle
- **Large Positions**: Many lots stress test

## Running Performance Tests

### Option 1: REST API Script (Recommended)

The simplest way to run performance tests is using the shell script that calls the REST API:

```bash
# Start the application first
mvn spring-boot:run -pl api

# In another terminal, run the performance test
./scripts/performance-test-simple.sh

# Or with custom base URL
BASE_URL=http://localhost:8081 ./scripts/performance-test-simple.sh
```

### Option 2: Java Performance Test Class

The `PositionServicePerformanceTest` class provides comprehensive performance tests:

```bash
mvn test -pl application -Dtest=PositionServicePerformanceTest
```

**Note**: This requires proper Spring Boot test context setup with all dependencies.

## Test Scenarios

### Test 1: Hotpath Performance - Sequential Trades

**Purpose**: Measure latency for typical hotpath processing

**Test Details**:
- 50-100 sequential trades
- Mix of NEW_TRADE, INCREASE, DECREASE
- Current/forward-dated trades
- Measures: Average latency, P50, P95, P99

**Expected Results**:
- Average latency: < 100ms
- P99 latency: < 200ms
- Throughput: > 10 trades/second

### Test 2: Coldpath Performance - Backdated Trade

**Purpose**: Measure latency for backdated trade recalculation

**Test Details**:
- Create position with 10-20 forward-dated trades
- Process 5 backdated trades requiring recalculation
- Full event stream replay
- Measures: Average latency, P95, P99

**Expected Results**:
- Average latency: < 1000ms (coldpath is slower due to replay)
- P99 latency: < 2000ms

### Test 3: Batch Processing Performance

**Purpose**: Test concurrent processing of multiple positions

**Test Details**:
- 20-50 positions processed in parallel
- 10 trades per position
- Thread pool execution
- Measures: Total time, throughput, average latency

**Expected Results**:
- Throughput: > 5 trades/second (concurrent)
- Average latency: < 200ms per trade

### Test 4: Mixed Workload Performance

**Purpose**: Test mix of hotpath and coldpath trades

**Test Details**:
- 30 hotpath trades
- 5 coldpath trades
- Measures: Separate latencies for each path

**Expected Results**:
- Hotpath average: < 100ms
- Hotpath P99: < 200ms
- Coldpath average: < 1000ms

### Test 5: Large Position Performance

**Purpose**: Stress test with many lots

**Test Details**:
- 500 lots on a single position
- Sequential processing
- Measures: Average latency per lot, total time

**Expected Results**:
- Average latency: < 50ms even with many lots
- Linear scaling (latency doesn't degrade significantly)

### Test 6: Position Lifecycle Performance

**Purpose**: Test complete position lifecycle

**Test Details**:
- Create position
- Add to position
- Partial close
- Full close
- Reopen
- Measures: Latency for each operation

**Expected Results**:
- Average latency: < 100ms per operation
- All operations complete successfully

### Test 7: Concurrent Hotpath Performance

**Purpose**: Test concurrent processing of multiple positions

**Test Details**:
- 20 positions
- 10 trades per position
- 10 threads
- Measures: Throughput, average latency

**Expected Results**:
- Throughput: > 5 trades/second
- Average latency: < 200ms

## Performance Metrics

### Key Metrics Tracked

1. **Latency**:
   - Average (mean)
   - P50 (median)
   - P95 (95th percentile)
   - P99 (99th percentile)
   - Maximum

2. **Throughput**:
   - Trades per second
   - Total trades processed
   - Total time

3. **Resource Usage**:
   - Database connection pool usage
   - Cache hit rates
   - Memory usage

## Performance Targets

### Hotpath Targets

- **Average Latency**: < 100ms
- **P99 Latency**: < 200ms
- **Throughput**: > 10 trades/second (sequential)
- **Throughput**: > 5 trades/second (concurrent)

### Coldpath Targets

- **Average Latency**: < 1000ms
- **P99 Latency**: < 2000ms
- **Throughput**: > 1 recalculation/second

## Running Tests Against Live Application

1. **Start Infrastructure**:
   ```bash
   docker-compose up -d postgres redis kafka
   ```

2. **Start Application**:
   ```bash
   mvn spring-boot:run -pl api
   ```

3. **Run Performance Test Script**:
   ```bash
   ./scripts/performance-test-simple.sh
   ```

## Interpreting Results

### Good Performance Indicators

- ✅ Average latency < target
- ✅ P99 latency < 2x target
- ✅ Throughput meets requirements
- ✅ No errors or exceptions
- ✅ Consistent latency (low variance)

### Performance Issues to Watch

- ⚠️ Latency spikes (check database, cache, network)
- ⚠️ Degrading performance over time (memory leaks, connection pool exhaustion)
- ⚠️ High P99 relative to average (tail latency issues)
- ⚠️ Errors or timeouts (resource constraints)

## Performance Optimization Tips

1. **Database**:
   - Ensure indexes are created
   - Monitor connection pool usage
   - Check for N+1 queries

2. **Cache**:
   - Monitor cache hit rates
   - Adjust TTL if needed
   - Check cache eviction patterns

3. **Transaction Management**:
   - Minimize transaction duration
   - Use appropriate isolation levels
   - Batch operations when possible

4. **Event Store**:
   - Monitor partition sizes
   - Check for partition pruning
   - Optimize event queries

## Continuous Performance Monitoring

For production, set up:
- **APM Tools**: New Relic, Datadog, AppDynamics
- **Metrics**: Prometheus + Grafana
- **Logging**: Structured logs with correlation IDs
- **Alerting**: P99 latency thresholds, error rates
