package com.bank.esps.application.service;

import com.bank.esps.domain.cache.CacheService;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.enums.TradeSequenceStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.messaging.MessageProducer;
import com.bank.esps.domain.model.LotAllocationResult;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Hotpath service for processing current/forward-dated trades synchronously
 * Target latency: <100ms p99
 */
@Service
public class HotpathPositionService {
    
    private static final Logger log = LoggerFactory.getLogger(HotpathPositionService.class);
    
    private final EventStoreService eventStoreService;
    private final CacheService cacheService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final LotLogic lotLogic;
    private final TradeClassifier tradeClassifier;
    private final IdempotencyService idempotencyService;
    private final MessageProducer messageProducer;
    private final TradeValidationService validationService;
    private final CorrelationIdService correlationIdService;
    
    @Value("${app.kafka.topics.backdated-trades:backdated-trades}")
    private String backdatedTradesTopic;
    
    @Value("${app.kafka.topics.trade-applied-events:trade-applied-events}")
    private String tradeAppliedEventsTopic;
    
    @Value("${app.kafka.topics.provisional-trade-events:provisional-trade-events}")
    private String provisionalTradeEventsTopic;
    
    public HotpathPositionService(
            EventStoreService eventStoreService,
            CacheService cacheService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            LotLogic lotLogic,
            TradeClassifier tradeClassifier,
            IdempotencyService idempotencyService,
            MessageProducer messageProducer,
            TradeValidationService validationService,
            CorrelationIdService correlationIdService) {
        this.eventStoreService = eventStoreService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.lotLogic = lotLogic;
        this.tradeClassifier = tradeClassifier;
        this.idempotencyService = idempotencyService;
        this.messageProducer = messageProducer;
        this.validationService = validationService;
        this.correlationIdService = correlationIdService;
    }
    
    /**
     * Process trade in hotpath (current/forward-dated) or route to coldpath (backdated)
     */
    @Transactional
    public PositionState processTrade(TradeEvent tradeEvent) {
        // Generate correlation ID if not present
        String correlationId = correlationIdService.getCurrentCorrelationId();
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = correlationIdService.generateCorrelationId();
        }
        
        // Note: Validation is now done in TradeController before calling this method
        // This ensures validation happens early and failed trades go to DLQ
        
        // Check idempotency
        if (idempotencyService.isAlreadyProcessed(tradeEvent.getTradeId())) {
            log.info("Trade {} already processed, returning cached state", tradeEvent.getTradeId());
            String positionKey = tradeEvent.getPositionKey();
            if (positionKey == null || positionKey.isEmpty()) {
                positionKey = generatePositionKey(tradeEvent);
            }
            return getCurrentState(positionKey);
        }
        
        String positionKey = tradeEvent.getPositionKey();
        if (positionKey == null || positionKey.isEmpty()) {
            positionKey = generatePositionKey(tradeEvent);
            tradeEvent.setPositionKey(positionKey);
        }
        
        // Load current state
        PositionState currentState = getCurrentState(positionKey);
        
        // Classify trade
        TradeSequenceStatus sequenceStatus = tradeClassifier.classifyTrade(tradeEvent, currentState);
        
        // Route based on classification
        if (sequenceStatus == TradeSequenceStatus.BACKDATED) {
            return handleBackdatedTrade(tradeEvent, currentState);
        } else {
            return handleCurrentOrForwardDatedTrade(tradeEvent, currentState, sequenceStatus);
        }
    }
    
    /**
     * Handle current-dated or forward-dated trades synchronously
     */
    private PositionState handleCurrentOrForwardDatedTrade(
            TradeEvent tradeEvent, PositionState currentState, TradeSequenceStatus sequenceStatus) {
        
        log.info("Processing {} trade {} in hotpath", sequenceStatus, tradeEvent.getTradeId());
        
        // Apply trade
        PositionState newState = applyTrade(currentState, tradeEvent);
        
        // Save event with correlation ID
        int nextVersion = eventStoreService.getNextVersion(tradeEvent.getPositionKey());
        String correlationId = correlationIdService.getCurrentCorrelationId();
        String causationId = tradeEvent.getTradeId(); // Use trade ID as causation ID
        eventStoreService.appendEvent(tradeEvent.getPositionKey(), tradeEvent, nextVersion, 
                correlationId, causationId);
        newState.setVersion(nextVersion);
        
        // Record idempotency
        idempotencyService.recordProcessed(tradeEvent.getTradeId(), 
                tradeEvent.getPositionKey(), nextVersion);
        
        // Save snapshot with RECONCILED status
        eventStoreService.saveSnapshot(tradeEvent.getPositionKey(), newState, 
                ReconciliationStatus.RECONCILED, null);
        
        // Update cache
        cacheService.put("position:" + tradeEvent.getPositionKey(), newState);
        
        // Publish trade applied event (async, non-blocking)
        try {
            String eventJson = objectMapper.writeValueAsString(newState);
            messageProducer.send(tradeAppliedEventsTopic, tradeEvent.getPositionKey(), eventJson);
        } catch (Exception e) {
            log.warn("Failed to publish trade applied event for trade {}", tradeEvent.getTradeId(), e);
            // Don't fail the transaction
        }
        
        log.info("Processed {} trade {} for position: {}, version: {}", 
                sequenceStatus, tradeEvent.getTradeId(), tradeEvent.getPositionKey(), nextVersion);
        return newState;
    }
    
    /**
     * Handle backdated trades: route to coldpath and create provisional position
     */
    private PositionState handleBackdatedTrade(TradeEvent tradeEvent, PositionState currentState) {
        log.info("Routing backdated trade {} to coldpath", tradeEvent.getTradeId());
        
        // Route to coldpath topic (async, non-blocking)
        try {
            String tradeJson = objectMapper.writeValueAsString(tradeEvent);
            messageProducer.send(backdatedTradesTopic, tradeEvent.getPositionKey(), tradeJson);
            log.debug("Published backdated trade {} to coldpath topic", tradeEvent.getTradeId());
        } catch (Exception e) {
            log.error("Failed to route backdated trade {} to coldpath", tradeEvent.getTradeId(), e);
            // Continue with provisional position creation even if routing fails
        }
        
        // Calculate provisional position (dirty estimate)
        PositionState provisionalState = applyTrade(currentState, tradeEvent);
        
        // Save provisional snapshot
        int nextVersion = eventStoreService.getNextVersion(tradeEvent.getPositionKey());
        eventStoreService.saveSnapshot(tradeEvent.getPositionKey(), provisionalState, 
                ReconciliationStatus.PROVISIONAL, tradeEvent.getTradeId());
        provisionalState.setVersion(nextVersion);
        
        // Update cache with provisional state
        cacheService.put("position:" + tradeEvent.getPositionKey(), provisionalState);
        
        // Publish provisional trade event (async, non-blocking)
        try {
            String eventJson = objectMapper.writeValueAsString(provisionalState);
            messageProducer.send(provisionalTradeEventsTopic, tradeEvent.getPositionKey(), eventJson);
        } catch (Exception e) {
            log.warn("Failed to publish provisional trade event for trade {}", tradeEvent.getTradeId(), e);
        }
        
        log.info("Created provisional position for backdated trade {}: position={}, version={}", 
                tradeEvent.getTradeId(), tradeEvent.getPositionKey(), nextVersion);
        
        return provisionalState;
    }
    
    /**
     * Apply trade to position state
     */
    private PositionState applyTrade(PositionState currentState, TradeEvent tradeEvent) {
        if (currentState == null || currentState.getPositionKey() == null) {
            currentState = new PositionState(
                    tradeEvent.getPositionKey(),
                    tradeEvent.getAccount(),
                    tradeEvent.getInstrument(),
                    tradeEvent.getCurrency()
            );
        }
        
        java.math.BigDecimal quantity = tradeEvent.getQuantity() != null 
                ? tradeEvent.getQuantity() 
                : java.math.BigDecimal.ZERO;
        java.math.BigDecimal price = tradeEvent.getPrice() != null 
                ? tradeEvent.getPrice() 
                : java.math.BigDecimal.ZERO;
        
        if (quantity.compareTo(java.math.BigDecimal.ZERO) > 0) {
            // Increase: Add new tax lot
            lotLogic.addLot(currentState, quantity, price, 
                    tradeEvent.getTradeDate(), tradeEvent.getSettlementDate());
        } else if (quantity.compareTo(java.math.BigDecimal.ZERO) < 0) {
            // Decrease: Reduce existing lots using FIFO
            java.math.BigDecimal qtyToReduce = quantity.abs();
            LotAllocationResult result = lotLogic.reduceLots(
                    currentState, qtyToReduce, LotLogic.TaxLotMethod.FIFO, price);
            
            if (!result.isFullyAllocated()) {
                log.warn("Could not fully reduce position. Trade: {}, Requested: {}, Remaining: {}", 
                        tradeEvent.getTradeId(), qtyToReduce, result.getRemainingQtyToAllocate());
            }
        }
        
        return currentState;
    }
    
    @Transactional(readOnly = true)
    public PositionState getCurrentState(String positionKey) {
        // Try cache first
        return cacheService.get("position:" + positionKey, PositionState.class)
                .orElseGet(() -> {
                    // Try snapshot
                    PositionState snapshot = eventStoreService.loadSnapshot(positionKey);
                    if (snapshot != null) {
                        // Replay events since snapshot
                        List<EventEntity> events = eventStoreService.getEvents(positionKey);
                        int snapshotVersion = snapshot.getVersion();
                        PositionState state = snapshot;
                        
                        for (EventEntity event : events) {
                            if (event.getEventVer() > snapshotVersion) {
                                try {
                                    TradeEvent tradeEvent = objectMapper.readValue(event.getEventData(), TradeEvent.class);
                                    state = applyTrade(state, tradeEvent);
                                } catch (Exception e) {
                                    log.error("Failed to deserialize event: {}", event.getId(), e);
                                }
                            }
                        }
                        
                        cacheService.put("position:" + positionKey, state);
                        return state;
                    }
                    
                    // Replay all events
                    List<EventEntity> events = eventStoreService.getEvents(positionKey);
                    PositionState state = new PositionState(positionKey, "", "", "");
                    
                    for (EventEntity event : events) {
                        try {
                            TradeEvent tradeEvent = objectMapper.readValue(event.getEventData(), TradeEvent.class);
                            state = applyTrade(state, tradeEvent);
                        } catch (Exception e) {
                            log.error("Failed to deserialize event: {}", event.getId(), e);
                        }
                    }
                    
                    return state;
                });
    }
    
    private String generatePositionKey(TradeEvent tradeEvent) {
        return String.format("%s:%s:%s", 
                tradeEvent.getAccount(),
                tradeEvent.getInstrument(),
                tradeEvent.getCurrency());
    }
}
