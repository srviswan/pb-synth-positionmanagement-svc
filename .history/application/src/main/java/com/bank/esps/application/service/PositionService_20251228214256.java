package com.bank.esps.application.service;

import com.bank.esps.domain.cache.CacheService;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.LotAllocationResult;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.TaxLot;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for managing positions
 */
@Service
public class PositionService {
    
    private static final Logger log = LoggerFactory.getLogger(PositionService.class);
    
    private final EventStoreService eventStoreService;
    private final CacheService cacheService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final LotLogic lotLogic;
    private final TradeClassifier tradeClassifier;
    private final IdempotencyService idempotencyService;
    
    public PositionService(EventStoreService eventStoreService,
                          CacheService cacheService,
                          com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                          LotLogic lotLogic,
                          TradeClassifier tradeClassifier,
                          IdempotencyService idempotencyService) {
        this.eventStoreService = eventStoreService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.lotLogic = lotLogic;
        this.tradeClassifier = tradeClassifier;
        this.idempotencyService = idempotencyService;
    }
    
    @Transactional
    public PositionState processTrade(TradeEvent tradeEvent) {
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
        
        // Classify trade (for future hotpath/coldpath routing)
        com.bank.esps.domain.enums.TradeSequenceStatus sequenceStatus = 
                tradeClassifier.classifyTrade(tradeEvent, currentState);
        log.debug("Trade {} classified as: {}", tradeEvent.getTradeId(), sequenceStatus);
        
        // Apply trade
        PositionState newState = applyTrade(currentState, tradeEvent);
        
        // Save event
        int nextVersion = eventStoreService.getNextVersion(positionKey);
        eventStoreService.appendEvent(positionKey, tradeEvent, nextVersion);
        newState.setVersion(nextVersion);
        
        // Record idempotency
        idempotencyService.recordProcessed(tradeEvent.getTradeId(), positionKey, nextVersion);
        
        // Save snapshot
        eventStoreService.saveSnapshot(positionKey, newState);
        
        // Update cache
        cacheService.put("position:" + positionKey, newState);
        
        log.info("Processed trade for position: {}, version: {}, sequenceStatus: {}", 
                positionKey, nextVersion, sequenceStatus);
        return newState;
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
                        
                        // Cache the reconstructed state
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
    
    private PositionState applyTrade(PositionState currentState, TradeEvent tradeEvent) {
        if (currentState == null || currentState.getPositionKey() == null) {
            currentState = new PositionState(
                    tradeEvent.getPositionKey(),
                    tradeEvent.getAccount(),
                    tradeEvent.getInstrument(),
                    tradeEvent.getCurrency()
            );
        }
        
        BigDecimal quantity = tradeEvent.getQuantity() != null ? tradeEvent.getQuantity() : BigDecimal.ZERO;
        BigDecimal price = tradeEvent.getPrice() != null ? tradeEvent.getPrice() : BigDecimal.ZERO;
        
        // Handle positive quantity (increase) or negative quantity (decrease)
        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            // Increase: Add new tax lot
            lotLogic.addLot(currentState, quantity, price, 
                    tradeEvent.getTradeDate(), tradeEvent.getSettlementDate());
            log.debug("Added lot for trade: tradeId={}, qty={}, price={}", 
                    tradeEvent.getTradeId(), quantity, price);
        } else if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            // Decrease: Reduce existing lots using FIFO (default)
            BigDecimal qtyToReduce = quantity.abs();
            LotAllocationResult result = lotLogic.reduceLots(
                    currentState, qtyToReduce, LotLogic.TaxLotMethod.FIFO, price);
            
            if (!result.isFullyAllocated()) {
                log.warn("Could not fully reduce position. Trade: {}, Requested: {}, Remaining: {}", 
                        tradeEvent.getTradeId(), qtyToReduce, result.getRemainingQtyToAllocate());
            }
            
            log.debug("Reduced lots for trade: tradeId={}, qty={}, allocatedLots={}", 
                    tradeEvent.getTradeId(), qtyToReduce, result.getAllocatedLotIds().size());
        } else {
            log.warn("Zero quantity trade ignored: tradeId={}", tradeEvent.getTradeId());
        }
        
        return currentState;
    }
    
    private String generatePositionKey(TradeEvent tradeEvent) {
        return String.format("%s:%s:%s", 
                tradeEvent.getAccount(),
                tradeEvent.getInstrument(),
                tradeEvent.getCurrency());
    }
    
    private List<EventEntity> getEventsSinceSnapshot(PositionState snapshot, String positionKey) {
        List<EventEntity> allEvents = eventStoreService.getEvents(positionKey);
        return allEvents.stream()
                .filter(e -> e.getEventVer() > snapshot.getVersion())
                .toList();
    }
}
