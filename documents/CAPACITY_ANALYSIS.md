# Service Capacity Analysis

## Current Configuration Limits

### 1. HTTP Request Thread Pool (Tomcat - Spring Boot Default)
- **Default Max Threads**: 200 (Spring Boot 3.x default for Tomcat)
- **Min Spare Threads**: 10
- **Max Connections**: 10,000
- **Accept Count**: 100 (queue size when all threads are busy)
- **Connection Timeout**: 20 seconds

**Theoretical Max Concurrent Requests**: ~200 (limited by thread pool)

### 2. Database Connection Pool (HikariCP)
- **Maximum Pool Size**: 50 connections
- **Minimum Idle**: 10 connections
- **Connection Timeout**: 20 seconds
- **Leak Detection Threshold**: 60 seconds

**Bottleneck**: Database operations are limited to 50 concurrent connections

### 3. Redis Connection Pool (Lettuce)
- **Max Active Connections**: 50
- **Max Idle**: 20
- **Min Idle**: 10

**Bottleneck**: Cache operations limited to 50 concurrent connections

### 4. Kafka Consumer Concurrency
- **Default**: 1 thread per partition
- **Backdated Trades Topic**: Typically 8-16 partitions (based on architecture docs)
- **Concurrent Consumers**: 8-16 threads for coldpath processing

### 5. Message Producer (Kafka/Solace)
- **Kafka**: Async by default, limited by network and broker capacity
- **Solace**: Connection pool managed by Solace client

## Capacity Calculation

### Synchronous Request Capacity

**Primary Bottleneck**: Database connection pool (50 connections)

**Calculation**:
- Each request typically requires:
  - 1 database connection (for read/write operations)
  - 1 cache connection (for Redis lookups)
  - Processing time: ~10-50ms per request (depending on operation)

**Maximum Concurrent Requests**: ~50 requests/second (assuming 1 second average response time)
- With faster response times (100ms): ~500 requests/second
- With slower response times (2 seconds): ~25 requests/second

### Asynchronous Request Capacity

**For async operations** (e.g., `/api/diagnostics/recalculate/async`):
- Limited by message broker capacity (Kafka/Solace)
- Can handle much higher throughput (thousands per second)
- Actual processing happens asynchronously via message consumers

## Real-World Capacity Estimates

### Light Load (Simple Reads)
- **Throughput**: 500-1,000 requests/second
- **Response Time**: 10-50ms
- **Concurrent Users**: 200-500

### Medium Load (Mixed Operations)
- **Throughput**: 200-500 requests/second
- **Response Time**: 50-200ms
- **Concurrent Users**: 100-200

### Heavy Load (Complex Writes)
- **Throughput**: 50-200 requests/second
- **Response Time**: 200ms-2 seconds
- **Concurrent Users**: 50-100

### Peak Load (All Writes)
- **Throughput**: 25-100 requests/second
- **Response Time**: 500ms-5 seconds
- **Concurrent Users**: 25-50

## Bottlenecks and Recommendations

### Current Bottlenecks (in order of impact):

1. **Database Connection Pool (50)** - Primary bottleneck
   - **Recommendation**: Increase to 100-200 for higher throughput
   - **Impact**: Can increase capacity by 2-4x

2. **HTTP Thread Pool (200)** - Secondary bottleneck
   - **Recommendation**: Increase to 500-1000 for high-traffic scenarios
   - **Impact**: Can handle more concurrent requests

3. **Redis Connection Pool (50)** - Tertiary bottleneck
   - **Recommendation**: Increase to 100-200
   - **Impact**: Better cache performance under load

### Configuration Recommendations for Higher Capacity

```yaml
# application.yml additions
server:
  tomcat:
    threads:
      max: 500          # Increase from default 200
      min-spare: 50     # Increase from default 10
    max-connections: 10000
    accept-count: 500  # Increase queue size
    connection-timeout: 20000

spring:
  datasource:
    hikari:
      maximum-pool-size: 100  # Increase from 50
      minimum-idle: 20        # Increase from 10
      connection-timeout: 30000
      
  data:
    redis:
      lettuce:
        pool:
          max-active: 100  # Increase from 50
          max-idle: 50     # Increase from 20
          min-idle: 20     # Increase from 10
```

### Horizontal Scaling

For production environments requiring higher capacity:

1. **Multiple Instances**: Deploy 2-10 instances behind a load balancer
   - Each instance: 50-200 req/sec
   - Total capacity: 100-2,000 req/sec

2. **Database Scaling**: 
   - Read replicas for read-heavy workloads
   - Connection pooling across instances

3. **Cache Clustering**: 
   - Redis cluster for distributed caching
   - Reduces cache connection pressure

## Load Test Results

Based on the load test script configuration:
- **Ramp-up Phase**: Gradual increase to target load
- **Sustained Load**: Maintains steady state
- **Spike Test**: Tests burst capacity

Actual numbers depend on:
- Hardware specifications
- Network latency
- Database performance
- Message broker capacity

## Monitoring Recommendations

Monitor these metrics to understand actual capacity:
- Request rate (requests/second)
- Response time (p50, p95, p99)
- Database connection pool utilization
- Thread pool utilization
- Error rate
- Circuit breaker states

## Summary

**Current Maximum Capacity** (single instance):
- **Conservative Estimate**: 50-100 requests/second
- **Optimistic Estimate**: 200-500 requests/second
- **Theoretical Maximum**: ~500 requests/second (with optimal conditions)

**With Recommended Configuration Changes**:
- **Conservative Estimate**: 100-200 requests/second
- **Optimistic Estimate**: 500-1,000 requests/second
- **Theoretical Maximum**: ~1,000 requests/second

**With Horizontal Scaling (5 instances)**:
- **Total Capacity**: 500-5,000 requests/second

**Note**: Actual capacity depends heavily on:
- Request complexity (read vs write operations)
- Database performance
- Network latency
- Hardware resources (CPU, memory, I/O)
- Message broker capacity
