package com.bank.esps.api.controller;

import com.bank.esps.application.service.HotpathPositionService;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    
    @Autowired
    private SnapshotRepository snapshotRepository;
    
    @Autowired
    private HotpathPositionService hotpathPositionService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
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
    
    /**
     * Get P&L summary for a position
     */
    @GetMapping("/events/position/{positionKey}/pnl")
    public ResponseEntity<Map<String, Object>> getPnLSummary(@PathVariable String positionKey) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
            
            if (events.isEmpty()) {
                response.put("status", "not_found");
                response.put("message", "No events found for position");
                return ResponseEntity.status(404).body(response);
            }
            
            // Parse P&L from metaLots in DECREASE events
            java.math.BigDecimal totalPnL = java.math.BigDecimal.ZERO;
            List<Map<String, Object>> pnlDetails = new java.util.ArrayList<>();
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            
            for (EventEntity event : events) {
                if (event.getEventType() == com.bank.esps.domain.enums.EventType.DECREASE && 
                    event.getMetaLots() != null && !event.getMetaLots().isEmpty()) {
                    try {
                        Map<String, Object> allocations = mapper.readValue(event.getMetaLots(), Map.class);
                        
                        for (Map.Entry<String, Object> entry : allocations.entrySet()) {
                            String lotId = entry.getKey();
                            Map<String, Object> allocation = (Map<String, Object>) entry.getValue();
                            
                            if (allocation.containsKey("realizedPnL")) {
                                Object pnlObj = allocation.get("realizedPnL");
                                java.math.BigDecimal pnl = pnlObj instanceof Number ? 
                                    java.math.BigDecimal.valueOf(((Number) pnlObj).doubleValue()) :
                                    new java.math.BigDecimal(pnlObj.toString());
                                
                                totalPnL = totalPnL.add(pnl);
                                
                                Map<String, Object> detail = new HashMap<>();
                                detail.put("eventVersion", event.getEventVer());
                                detail.put("eventType", event.getEventType().name());
                                detail.put("effectiveDate", event.getEffectiveDate().toString());
                                detail.put("lotId", lotId);
                                detail.put("qty", allocation.get("qty"));
                                detail.put("closePrice", allocation.get("price"));
                                detail.put("realizedPnL", pnl);
                                pnlDetails.add(detail);
                            }
                        }
                    } catch (Exception e) {
                        // Skip if parsing fails
                        response.put("parseWarning", "Some events could not be parsed: " + e.getMessage());
                    }
                }
            }
            
            response.put("positionKey", positionKey);
            response.put("totalRealizedPnL", totalPnL);
            response.put("pnlDetails", pnlDetails);
            response.put("status", "success");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Get snapshot by position key (includes PriceQuantity schedule and current position quantity)
     */
    @GetMapping("/snapshot/{positionKey}")
    public ResponseEntity<Map<String, Object>> getSnapshot(@PathVariable String positionKey) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<SnapshotEntity> snapshotOpt = snapshotRepository.findById(positionKey);
            if (snapshotOpt.isPresent()) {
                SnapshotEntity snapshot = snapshotOpt.get();
                response.put("positionKey", snapshot.getPositionKey());
                response.put("lastVer", snapshot.getLastVer());
                response.put("uti", snapshot.getUti());
                response.put("status", snapshot.getStatus().name());
                response.put("reconciliationStatus", snapshot.getReconciliationStatus().name());
                response.put("lastUpdatedAt", snapshot.getLastUpdatedAt().toString());
                response.put("version", snapshot.getVersion());
                
                // Inflate PositionState to get current quantity
                try {
                    PositionState state = hotpathPositionService.inflateSnapshot(snapshot);
                    Map<String, Object> positionInfo = new HashMap<>();
                    positionInfo.put("currentQuantity", state.getTotalQty());
                    positionInfo.put("exposure", state.getExposure());
                    positionInfo.put("lotCount", state.getLotCount());
                    positionInfo.put("openLotsCount", state.getOpenLots().size());
                    response.put("position", positionInfo);
                } catch (Exception e) {
                    response.put("positionError", "Failed to inflate position state: " + e.getMessage());
                }
                
                // Parse summary metrics if present
                if (snapshot.getSummaryMetrics() != null && !snapshot.getSummaryMetrics().trim().isEmpty()) {
                    try {
                        Map<String, Object> summaryMetrics = objectMapper.readValue(
                            snapshot.getSummaryMetrics(), Map.class);
                        response.put("summaryMetrics", summaryMetrics);
                    } catch (Exception e) {
                        response.put("summaryMetrics", snapshot.getSummaryMetrics());
                    }
                }
                
                // Parse PriceQuantity schedule if present
                if (snapshot.getPriceQuantitySchedule() != null && !snapshot.getPriceQuantitySchedule().trim().isEmpty()) {
                    try {
                        Map<String, Object> schedule = objectMapper.readValue(
                            snapshot.getPriceQuantitySchedule(), Map.class);
                        response.put("priceQuantitySchedule", schedule);
                        
                        // Extract current quantity/price from schedule if available
                        if (schedule.containsKey("schedule") && schedule.get("schedule") instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> scheduleList = (List<Map<String, Object>>) schedule.get("schedule");
                            if (!scheduleList.isEmpty()) {
                                Map<String, Object> currentEntry = scheduleList.get(scheduleList.size() - 1);
                                Map<String, Object> currentScheduleInfo = new HashMap<>();
                                currentScheduleInfo.put("effectiveDate", currentEntry.get("effectiveDate"));
                                currentScheduleInfo.put("quantity", currentEntry.get("quantity"));
                                currentScheduleInfo.put("price", currentEntry.get("price"));
                                if (currentEntry.containsKey("notional")) {
                                    currentScheduleInfo.put("notional", currentEntry.get("notional"));
                                } else if (currentEntry.get("quantity") != null && currentEntry.get("price") != null) {
                                    // Calculate notional if not present
                                    try {
                                        java.math.BigDecimal qty = new java.math.BigDecimal(currentEntry.get("quantity").toString());
                                        java.math.BigDecimal price = new java.math.BigDecimal(currentEntry.get("price").toString());
                                        currentScheduleInfo.put("notional", qty.multiply(price));
                                    } catch (Exception ignored) {}
                                }
                                response.put("currentScheduleEntry", currentScheduleInfo);
                            }
                        }
                    } catch (Exception e) {
                        response.put("priceQuantitySchedule", snapshot.getPriceQuantitySchedule());
                        response.put("scheduleParseError", e.getMessage());
                    }
                }
                
                response.put("found", true);
                response.put("status", "success");
            } else {
                response.put("found", false);
                response.put("message", "Snapshot not found");
                response.put("status", "success");
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
}
