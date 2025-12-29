package com.bank.esps.application.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for Resilience4j circuit breakers, retries, and timeouts
 */
@Configuration
public class ResiliencyConfig {
    
    /**
     * Circuit breaker registry
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }
    
    /**
     * Retry registry
     */
    @Bean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.ofDefaults();
    }
    
    /**
     * Time limiter registry
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.ofDefaults();
    }
    
    /**
     * Circuit breaker for Contract Generation Service (hotpath critical)
     */
    @Bean("contractServiceCircuitBreaker")
    public CircuitBreaker contractServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f) // Open after 50% failures
                .waitDurationInOpenState(Duration.ofSeconds(60)) // Wait 60s before half-open
                .slidingWindowSize(10) // Last 10 calls
                .minimumNumberOfCalls(5) // Need at least 5 calls before opening
                .permittedNumberOfCallsInHalfOpenState(3) // Test with 3 calls in half-open
                .build();
        
        return registry.circuitBreaker("contractService", config);
    }
    
    /**
     * Circuit breaker for Database operations (hotpath)
     */
    @Bean("hotpathDatabaseCircuitBreaker")
    public CircuitBreaker hotpathDatabaseCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        
        return registry.circuitBreaker("hotpathDatabase", config);
    }
    
    /**
     * Circuit breaker for Database operations (coldpath)
     */
    @Bean("coldpathDatabaseCircuitBreaker")
    public CircuitBreaker coldpathDatabaseCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slidingWindowSize(20) // Larger window for coldpath
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(5)
                .build();
        
        return registry.circuitBreaker("coldpathDatabase", config);
    }
    
    /**
     * Circuit breaker for Kafka producers
     */
    @Bean("kafkaProducerCircuitBreaker")
    public CircuitBreaker kafkaProducerCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        
        return registry.circuitBreaker("kafkaProducer", config);
    }
    
    /**
     * Retry for hotpath operations (fast retries)
     */
    @Bean("hotpathRetry")
    public Retry hotpathRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(50)) // Base delay 50ms
                .retryExceptions(Exception.class)
                .build();
        
        return registry.retry("hotpath", config);
    }
    
    /**
     * Retry for coldpath operations (more retries, longer delays)
     */
    @Bean("coldpathRetry")
    public Retry coldpathRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofMillis(200)) // Base delay 200ms
                .retryExceptions(Exception.class)
                .build();
        
        return registry.retry("coldpath", config);
    }
    
    /**
     * Time limiter for hotpath database operations
     */
    @Bean("hotpathDatabaseTimeLimiter")
    public TimeLimiter hotpathDatabaseTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(50)) // 50ms timeout
                .build();
        
        return registry.timeLimiter("hotpathDatabase", config);
    }
    
    /**
     * Time limiter for contract service (hotpath)
     */
    @Bean("contractServiceTimeLimiter")
    public TimeLimiter contractServiceTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(40)) // 40ms timeout
                .build();
        
        return registry.timeLimiter("contractService", config);
    }
    
    /**
     * Time limiter for coldpath operations
     */
    @Bean("coldpathTimeLimiter")
    public TimeLimiter coldpathTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMinutes(5)) // 5 minute timeout
                .build();
        
        return registry.timeLimiter("coldpath", config);
    }
}
