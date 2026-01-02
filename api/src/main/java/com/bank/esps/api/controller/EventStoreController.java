package com.bank.esps.api.controller;

import com.bank.esps.application.service.EventStoreService;
import com.bank.esps.application.service.ColdpathRecalculationService;
import com.bank.esps.api.service.UserContextExtractor;
import com.bank.esps.domain.auth.AuthorizationService;
import com.bank.esps.domain.auth.PositionFunction;
import com.bank.esps.domain.auth.UserContext;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.messaging.MessageProducer;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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
    private final MessageProducer messageProducer;
    private final AuthorizationService authorizationService;
    private final UserContextExtractor userContextExtractor;
    
    @org.springframework.beans.factory.annotation.Value("${app.kafka.topics.backdated-trades:backdated-trades}")
    private String backdatedTradesTopic;
    
    public EventStoreController(EventStoreRepository eventStoreRepository,
                              SnapshotRepository snapshotRepository,
                              EventStoreService eventStoreService,
                              ColdpathRecalculationService coldpathRecalculationService,
                              ObjectMapper objectMapper,
                              MessageProducer messageProducer,
                              AuthorizationService authorizationService,
                              UserContextExtractor userContextExtractor) {
        this.eventStoreRepository = eventStoreRepository;
        this.snapshotRepository = snapshotRepository;
        this.eventStoreService = eventStoreService;
        this.coldpathRecalculationService = coldpathRecalculationService;
        this.objectMapper = objectMapper;
        this.messageProducer = messageProducer;
        this.authorizationService = authorizationService;
        this.userContextExtractor = userContextExtractor;
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
    public ResponseEntity<Map<String, Object>> triggerRecalculation(@RequestBody TradeEvent tradeEvent,
                                                                   HttpServletRequest request) {
        try {
            // Extract user context (set by AuthorizationFilter)
            UserContext userContext = (UserContext) request.getAttribute("userContext");
            if (userContext == null) {
                userContext = userContextExtractor.extract(request);
            }
            
            log.info("Manual recalculation triggered for trade: {}, userId: {}", 
                    tradeEvent.getTradeId(), userContext != null ? userContext.getUserId() : "anonymous");
            
            // Validate required fields
            if (tradeEvent.getPositionKey() == null || tradeEvent.getPositionKey().trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Position key is required for recalculation");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Check if position exists before attempting recalculation
            List<EventEntity> existingEvents = eventStoreService.getEvents(tradeEvent.getPositionKey());
            if (existingEvents.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Position does not exist. Cannot recalculate a position with no events.");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Check book access if applicable
            if (userContext != null && tradeEvent.getBook() != null) {
                if (!authorizationService.hasBookAccess(userContext.getUserId(), tradeEvent.getBook())) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "error");
                    response.put("message", "User does not have access to book: " + tradeEvent.getBook());
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(response);
                }
            }
            
            coldpathRecalculationService.recalculatePosition(tradeEvent);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Recalculation triggered for trade: " + tradeEvent.getTradeId());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for recalculation: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (org.springframework.transaction.TransactionSystemException e) {
            log.error("Transaction error during recalculation", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Transaction error during recalculation");
            if (e.getCause() != null) {
                response.put("details", e.getCause().getMessage());
            }
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("Error triggering recalculation", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("rollback")) {
                errorMessage = "Transaction rollback error - the position may not exist or may be in an invalid state";
            }
            response.put("message", errorMessage != null ? errorMessage : "Internal server error during recalculation");
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Async recalculation endpoint - publishes trade to backdated-trades topic
     * This is a Plan B approach that avoids transaction rollback issues
     */
    @PostMapping("/recalculate/async")
    public ResponseEntity<Map<String, Object>> triggerAsyncRecalculation(@RequestBody TradeEvent tradeEvent,
                                                                         HttpServletRequest request) {
        try {
            // Extract user context (set by AuthorizationFilter)
            UserContext userContext = (UserContext) request.getAttribute("userContext");
            if (userContext == null) {
                userContext = userContextExtractor.extract(request);
            }
            
            log.info("Async recalculation triggered for trade: {}, userId: {}", 
                    tradeEvent.getTradeId(), userContext != null ? userContext.getUserId() : "anonymous");
            
            // Validate required fields
            if (tradeEvent.getPositionKey() == null || tradeEvent.getPositionKey().trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Position key is required for recalculation");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Validate that position exists
            List<EventEntity> existingEvents = eventStoreService.getEvents(tradeEvent.getPositionKey());
            if (existingEvents.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Position does not exist. Cannot recalculate a position with no events.");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Check book access if applicable
            if (userContext != null && tradeEvent.getBook() != null) {
                if (!authorizationService.hasBookAccess(userContext.getUserId(), tradeEvent.getBook())) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "error");
                    response.put("message", "User does not have access to book: " + tradeEvent.getBook());
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(response);
                }
            }
            
            // Serialize trade event to JSON
            String tradeJson = objectMapper.writeValueAsString(tradeEvent);
            
            // Publish to backdated-trades topic with user context (will be processed by KafkaListener)
            messageProducer.send(backdatedTradesTopic, tradeEvent.getPositionKey(), tradeJson, userContext);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "accepted");
            response.put("message", "Recalculation request queued for async processing. Trade: " + tradeEvent.getTradeId());
            response.put("tradeId", tradeEvent.getTradeId());
            response.put("positionKey", tradeEvent.getPositionKey());
            return ResponseEntity.accepted().body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for async recalculation: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error triggering async recalculation", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage() != null ? e.getMessage() : "Internal server error during async recalculation");
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
