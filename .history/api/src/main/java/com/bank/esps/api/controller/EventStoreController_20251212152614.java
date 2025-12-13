package com.bank.esps.api.controller;

import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Diagnostic controller for event store
 * Helps debug event store issues
 */
@RestController
@RequestMapping("/api/diagnostics")
public class EventStoreController {
    
    @Autowired
    private EventStoreRepository eventStoreRepository;
    
    /**
     * Get total event count
     */
    @GetMapping("/events/count")
    public ResponseEntity<Map<String, Object>> getEventCount() {
        Map<String, Object> response = new HashMap<>();
        try {
            long count = eventStoreRepository.count();
            response.put("totalEvents", count);
            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get events by position key
     */
    @GetMapping("/events/position/{positionKey}")
    public ResponseEntity<Map<String, Object>> getEventsByPosition(@PathVariable String positionKey) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
            response.put("positionKey", positionKey);
            response.put("eventCount", events.size());
            response.put("events", events.stream().map(e -> Map.of(
                    "version", e.getEventVer(),
                    "type", e.getEventType().name(),
                    "effectiveDate", e.getEffectiveDate().toString(),
                    "occurredAt", e.getOccurredAt().toString()
            )).toList());
            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get specific event
     */
    @GetMapping("/events/position/{positionKey}/version/{version}")
    public ResponseEntity<Map<String, Object>> getEvent(
            @PathVariable String positionKey,
            @PathVariable Long version) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<EventEntity> event = eventStoreRepository.findById(
                    new EventEntity.EventEntityId(positionKey, version));
            if (event.isPresent()) {
                EventEntity e = event.get();
                response.put("found", true);
                response.put("event", Map.of(
                        "positionKey", e.getPositionKey(),
                        "version", e.getEventVer(),
                        "type", e.getEventType().name(),
                        "effectiveDate", e.getEffectiveDate().toString(),
                        "occurredAt", e.getOccurredAt().toString(),
                        "payloadLength", e.getPayload() != null ? e.getPayload().length() : 0
                ));
            } else {
                response.put("found", false);
                response.put("message", "Event not found");
            }
            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get latest events
     */
    @GetMapping("/events/latest/{limit}")
    public ResponseEntity<Map<String, Object>> getLatestEvents(@PathVariable int limit) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Get all events and sort by occurredAt (this is not efficient for large datasets)
            List<EventEntity> allEvents = eventStoreRepository.findAll();
            List<EventEntity> latest = allEvents.stream()
                    .sorted((a, b) -> b.getOccurredAt().compareTo(a.getOccurredAt()))
                    .limit(limit)
                    .toList();
            
            response.put("limit", limit);
            response.put("returned", latest.size());
            response.put("events", latest.stream().map(e -> Map.of(
                    "positionKey", e.getPositionKey(),
                    "version", e.getEventVer(),
                    "type", e.getEventType().name(),
                    "effectiveDate", e.getEffectiveDate().toString(),
                    "occurredAt", e.getOccurredAt().toString()
            )).toList());
            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
}
