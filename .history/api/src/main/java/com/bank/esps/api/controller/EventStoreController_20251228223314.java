package com.bank.esps.api.controller;

import com.bank.esps.application.service.EventStoreService;
import com.bank.esps.application.service.ColdpathRecalculationService;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Event Store Controller for diagnostics and debugging
 */
@RestController
@RequestMapping("/api/diagnostics")
public class EventStoreController {
    
    private static final Logger log = LoggerFactory.getLogger(EventStoreController.class);
    
    private final EventStoreRepository eventStoreRepository;
    private final SnapshotRepository snapshotRepository;
    private final EventStoreService eventStoreService;
    private final ColdpathRecalculationService coldpathRecalculationService;
    private final ObjectMapper objectMapper;
    
    public EventStoreController(EventStoreRepository eventStoreRepository,
                              SnapshotRepository snapshotRepository,
                              EventStoreService eventStoreService,
                              ColdpathRecalculationService coldpathRecalculationService,
                              ObjectMapper objectMapper) {
        this.eventStoreRepository = eventStoreRepository;
        this.snapshotRepository = snapshotRepository;
        this.eventStoreService = eventStoreService;
        this.coldpathRecalculationService = coldpathRecalculationService;
        this.objectMapper = objectMapper;
    }
    
    @GetMapping("/events/count")
    public ResponseEntity<Map<String, Object>> getEventCount() {
        try {
            long count = eventStoreRepository.count();
            Map<String, Object> response = new HashMap<>();
            response.put("totalEvents", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting event count", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/events/position/{positionKey}")
    public ResponseEntity<List<EventEntity>> getEventsForPosition(@PathVariable String positionKey) {
        try {
            List<EventEntity> events = eventStoreService.getEvents(positionKey);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error getting events for position: {}", positionKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/events/position/{positionKey}/version/{version}")
    public ResponseEntity<EventEntity> getEventByVersion(
            @PathVariable String positionKey,
            @PathVariable int version) {
        try {
            List<EventEntity> events = eventStoreService.getEvents(positionKey);
            EventEntity event = events.stream()
                    .filter(e -> e.getEventVer() != null && e.getEventVer() == version)
                    .findFirst()
                    .orElse(null);
            
            if (event != null) {
                return ResponseEntity.ok(event);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting event by version: positionKey={}, version={}", positionKey, version, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/events/latest/{limit}")
    public ResponseEntity<List<EventEntity>> getLatestEvents(@PathVariable int limit) {
        try {
            if (limit > 100) limit = 100; // Cap at 100
            List<EventEntity> events = eventStoreRepository.findAll(
                    org.springframework.data.domain.Sort.by("createdAt").descending())
                    .stream()
                    .limit(limit)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error getting latest events", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/events/position/{positionKey}/pnl")
    public ResponseEntity<Map<String, Object>> getPnLSummary(@PathVariable String positionKey) {
        try {
            List<EventEntity> events = eventStoreService.getEvents(positionKey);
            
            // Calculate P&L from events (simplified - would need to track P&L in events)
            BigDecimal totalRealizedPnL = BigDecimal.ZERO;
            int closedLots = 0;
            
            Map<String, Object> response = new HashMap<>();
            response.put("positionKey", positionKey);
            response.put("totalEvents", events.size());
            response.put("totalRealizedPnL", totalRealizedPnL);
            response.put("closedLots", closedLots);
            response.put("note", "P&L calculation requires tracking in event metadata");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting P&L summary for position: {}", positionKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/snapshot/{positionKey}")
    public ResponseEntity<SnapshotEntity> getSnapshot(@PathVariable String positionKey) {
        try {
            return snapshotRepository.findByPositionKey(positionKey)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error getting snapshot for position: {}", positionKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> triggerRecalculation(@RequestBody TradeEvent tradeEvent) {
        try {
            log.info("Manual recalculation triggered for trade: {}", tradeEvent.getTradeId());
            coldpathRecalculationService.recalculatePosition(tradeEvent);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Recalculation triggered for trade: " + tradeEvent.getTradeId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error triggering recalculation", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
