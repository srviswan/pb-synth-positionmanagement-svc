package com.bank.esps.application.service;

import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for managing correlation and causation IDs
 */
@Service
public class CorrelationIdService {
    
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdService.class);
    
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CAUSATION_ID_KEY = "causationId";
    
    /**
     * Generate a new correlation ID
     */
    public String generateCorrelationId() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);
        log.debug("Generated correlation ID: {}", correlationId);
        return correlationId;
    }
    
    /**
     * Get current correlation ID from MDC
     */
    public String getCurrentCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }
    
    /**
     * Set correlation ID in MDC
     */
    public void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isEmpty()) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }
    }
    
    /**
     * Set causation ID in MDC
     */
    public void setCausationId(String causationId) {
        if (causationId != null && !causationId.isEmpty()) {
            MDC.put(CAUSATION_ID_KEY, causationId);
        }
    }
    
    /**
     * Get current causation ID from MDC
     */
    public String getCurrentCausationId() {
        return MDC.get(CAUSATION_ID_KEY);
    }
    
    /**
     * Clear correlation and causation IDs from MDC
     */
    public void clear() {
        MDC.remove(CORRELATION_ID_KEY);
        MDC.remove(CAUSATION_ID_KEY);
    }
    
    /**
     * Generate correlation ID with timestamp prefix for debugging
     */
    public String generateCorrelationIdWithTimestamp() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String correlationId = String.format("%s-%s", timestamp, uuid);
        MDC.put(CORRELATION_ID_KEY, correlationId);
        return correlationId;
    }
}
