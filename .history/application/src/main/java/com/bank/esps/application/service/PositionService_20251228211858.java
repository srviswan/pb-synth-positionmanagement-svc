package com.bank.esps.application.service;

import com.bank.esps.domain.cache.CacheService;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.TaxLot;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing positions
 */
@Service
public class PositionService {
    
    private static final Logger log = LoggerFactory.getLogger(PositionService.class);
    
    private final EventStoreService eventStoreService;
    private final CacheService cacheService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    
    public PositionService(EventStoreService eventStoreService,
                          CacheService cacheService,
                          com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.eventStoreService = eventStoreService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public PositionState processTrade(TradeEvent tradeEvent) {
        String positionKey = tradeEvent.getPositionKey();
        if (positionKey == null || positionKey.isEmpty()) {
            positionKey = generatePositionKey(tradeEvent);
            tradeEvent.setPositionKey(positionKey);
        }
        
        // Load current state
        PositionState currentState = getCurrentState(positionKey);
        
        // Apply trade
        PositionState newState = applyTrade(currentState, tradeEvent);
        
        // Save event
        int nextVersion = eventStoreService.getNextVersion(positionKey);
        eventStoreService.appendEvent(positionKey, tradeEvent, nextVersion);
        newState.setVersion(nextVersion);
        
        // Save snapshot
        eventStoreService.saveSnapshot(positionKey, newState);
        
        // Update cache
        cacheService.put("position:" + positionKey, newState);
        
        log.info("Processed trade for position: {}, version: {}", positionKey, nextVersion);
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
        
        // Create new tax lot
        TaxLot newLot = TaxLot.builder()
                .lotId(UUID.randomUUID().toString())
                .originalQty(tradeEvent.getQuantity())
                .remainingQty(tradeEvent.getQuantity())
                .costBasis(tradeEvent.getPrice())
                .currentRefPrice(tradeEvent.getPrice())
                .tradeDate(tradeEvent.getTradeDate())
                .settlementDate(tradeEvent.getSettlementDate())
                .settledQuantity(tradeEvent.getQuantity())
                .build();
        
        currentState.getOpenLots().add(newLot);
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
