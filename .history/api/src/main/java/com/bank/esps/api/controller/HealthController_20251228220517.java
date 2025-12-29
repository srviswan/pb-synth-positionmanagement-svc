package com.bank.esps.api.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller
 */
@RestController
@RequestMapping("/health")
public class HealthController {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    public HealthController(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }
    
    @GetMapping("/liveness")
    public ResponseEntity<Map<String, String>> liveness() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        
        // Check circuit breakers
        if (circuitBreakerRegistry != null) {
            Map<String, String> circuitBreakers = new HashMap<>();
            circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
                circuitBreakers.put(cb.getName(), cb.getState().name());
            });
            response.put("circuitBreakers", circuitBreakers);
            
            // Check if any critical circuit breaker is open
            boolean anyOpen = circuitBreakers.values().stream()
                    .anyMatch(state -> "OPEN".equals(state));
            if (anyOpen) {
                response.put("status", "DEGRADED");
                response.put("message", "Some circuit breakers are open");
            }
        }
        
        return ResponseEntity.ok(response);
    }
}
