package com.bank.esps.api.controller;

import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic controller for event store
 * Allows querying events to verify they are being saved
 */
@RestController
@RequestMapping("/api/diagnostics/events")
public class EventStoreController {
    
    private static final Logger log = LoggerFactory.getLogger(EventStoreController.class);
    
    @Autowired
    private EventStoreRepository eventStoreRepository;
    
    /**
     * Get all events for a position
     */
    @GetMapping("/position/{positionKey}")
    public ResponseEntity<Map<String, Object>> getEventsByPosition(@PathVariable String positionKey) {
        try {
            List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
            
            Map<String, Object> response = new HashMap<>();
            response.put("positionKey", positionKey);
            response.put("eventCount", events.size());
            response.put("events", events);
            
            log.info("Retrieved {} events for position {}", events.size(), positionKey);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving events for position {}", positionKey, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Get total event count
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getEventCount() {
        try {
            long count = eventStoreRepository.count();
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalEvents", count);
            
            log.info("Total events in store: {}", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error counting events", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Get events by correlation ID
     */
    @GetMapping("/correlation/{correlationId}")
    public ResponseEntity<Map<String, Object>> getEventsByCorrelationId(@PathVariable String correlationId) {
        try {
            List<EventEntity> events = eventStoreRepository.findByCorrelationId(correlationId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("correlationId", correlationId);
            response.put("eventCount", events.size());
            response.put("events", events);
            
            log.info("Retrieved {} events for correlation ID {}", events.size(), correlationId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving events for correlation ID {}", correlationId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Get latest version for a position
     */
    @GetMapping("/position/{positionKey}/latest-version")
    public ResponseEntity<Map<String, Object>> getLatestVersion(@PathVariable String positionKey) {
        try {
            Long latestVersion = eventStoreRepository.findLatestVersion(positionKey).orElse(0L);
            
            Map<String, Object> response = new HashMap<>();
            response.put("positionKey", positionKey);
            response.put("latestVersion", latestVersion);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving latest version for position {}", positionKey, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}
