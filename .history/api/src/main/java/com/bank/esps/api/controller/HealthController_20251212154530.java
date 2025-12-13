package com.bank.esps.api.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check endpoints
 * Provides liveness, readiness, and detailed health status
 */
@RestController
@RequestMapping("/health")
public class HealthController {
    
    @Autowired(required = false)
    private CircuitBreakerRegistry circuitBreakerRegistry;
    
    /**
     * Liveness probe - indicates service is running
     */
    @GetMapping("/liveness")
    public ResponseEntity<Map<String, String>> liveness() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        return ResponseEntity.ok(response);
    }
    
    /**
     * Readiness probe - indicates service can process requests
     */
    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> response = new HashMap<>();
        
        // Check circuit breakers
        Map<String, String> circuitBreakers = new HashMap<>();
        if (circuitBreakerRegistry != null) {
            circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
                circuitBreakers.put(cb.getName(), cb.getState().name());
            });
        }
        response.put("circuitBreakers", circuitBreakers);
        
        // Check if any critical circuit breaker is open
        boolean anyOpen = circuitBreakers.values().stream()
                .anyMatch(state -> "OPEN".equals(state));
        
        if (anyOpen) {
            response.put("status", "DEGRADED");
            response.put("message", "Some circuit breakers are open");
        } else {
            response.put("status", "UP");
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Detailed health check
     */
    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> detailed() {
        Map<String, Object> response = new HashMap<>();
        
        // Circuit breaker states
        Map<String, Map<String, Object>> circuitBreakers = new HashMap<>();
        if (circuitBreakerRegistry != null) {
            circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
                Map<String, Object> cbState = new HashMap<>();
                cbState.put("state", cb.getState().name());
                cbState.put("failureRate", String.valueOf(cb.getMetrics().getFailureRate()));
                cbState.put("numberOfFailedCalls", cb.getMetrics().getNumberOfFailedCalls());
                cbState.put("numberOfSuccessfulCalls", cb.getMetrics().getNumberOfSuccessfulCalls());
                circuitBreakers.put(cb.getName(), cbState);
            });
        }
        response.put("circuitBreakers", circuitBreakers);
        
        response.put("status", "UP");
        response.put("timestamp", java.time.OffsetDateTime.now());
        
        return ResponseEntity.ok(response);
    }
}
