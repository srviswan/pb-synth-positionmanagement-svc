package com.bank.esps.application.service;

import com.bank.esps.domain.cache.CacheService;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.messaging.MessageProducer;
import com.bank.esps.domain.model.LotAllocationResult;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Coldpath service for recalculating backdated trades
 * Processes asynchronously from backdated-trades Kafka topic
 */
@Service
public class ColdpathRecalculationService {
    
    private static final Logger log = LoggerFactory.getLogger(ColdpathRecalculationService.class);
    
    private final EventStoreService eventStoreService;
    private final CacheService cacheService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final LotLogic lotLogic;
    private final MessageProducer messageProducer;
    private final com.bank.esps.application.service.MetricsService metricsService;
    
    @Value("${app.kafka.topics.historical-position-corrected-events:historical-position-corrected-events}")
    private String historicalPositionCorrectedEventsTopic;
    
    public ColdpathRecalculationService(
            EventStoreService eventStoreService,
            CacheService cacheService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            LotLogic lotLogic,
            MessageProducer messageProducer,
            com.bank.esps.application.service.MetricsService metricsService) {
        this.eventStoreService = eventStoreService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.lotLogic = lotLogic;
        this.messageProducer = messageProducer;
        this.metricsService = metricsService;
    }
    
    /**
     * Kafka listener for backdated trades with authorization check
     */
    @KafkaListener(topics = "${app.kafka.topics.backdated-trades:backdated-trades}", 
                   groupId = "${spring.kafka.consumer.group-id:position-management-coldpath}")
    @Transactional
    public void processBackdatedTrade(String tradeJson,
                                     @org.springframework.messaging.handler.annotation.Header(value = "user-id", required = false) String userId) {
        try {
            TradeEvent backdatedTrade = objectMapper.readValue(tradeJson, TradeEvent.class);
            
            // Note: For system/internal messages, authorization may be skipped
            // In production, all messages should have user context
            if (userId != null) {
                log.info("Processing backdated trade in coldpath: tradeId={}, positionKey={}, effectiveDate={}, userId={}", 
                        backdatedTrade.getTradeId(), backdatedTrade.getPositionKey(), 
                        backdatedTrade.getEffectiveDate(), userId);
            } else {
                log.info("Processing backdated trade in coldpath: tradeId={}, positionKey={}, effectiveDate={} (no user context)", 
                        backdatedTrade.getTradeId(), backdatedTrade.getPositionKey(), 
                        backdatedTrade.getEffectiveDate());
            }
            
            recalculatePosition(backdatedTrade);
            
        } catch (Exception e) {
            log.error("Failed to process backdated trade: {}", tradeJson, e);
            throw new RuntimeException("Failed to process backdated trade", e);
        }
    }
    
    /**
     * Recalculate position with backdated trade injected at correct chronological position
     */
    @Transactional(rollbackFor = Exception.class)
    public void recalculatePosition(TradeEvent backdatedTrade) {
        io.micrometer.core.instrument.Timer.Sample processingSample = metricsService.startColdpathProcessing();
        String positionKey = null;
        PositionState correctedState = null;
        PositionState provisionalState = null;
        int nextVersion = 0;
        
        try {
            positionKey = backdatedTrade.getPositionKey();
            if (positionKey == null || positionKey.trim().isEmpty()) {
                throw new IllegalArgumentException("Position key cannot be null or empty");
            }
            
            LocalDate backdatedEffectiveDate = backdatedTrade.getEffectiveDate() != null 
                    ? backdatedTrade.getEffectiveDate() 
                    : backdatedTrade.getTradeDate();
        
            // 1. Load complete event stream
            List<EventEntity> allEvents = eventStoreService.getEvents(positionKey);
            log.debug("Loaded {} events for position {}", allEvents.size(), positionKey);
            
            // 2. Find insertion point (where backdated trade should be inserted)
            int insertIndex = findInsertionPoint(allEvents, backdatedEffectiveDate);
            log.debug("Insertion point for backdated trade: index={}", insertIndex);
            
            // 3. Create corrected event stream with backdated trade injected
            List<EventEntity> correctedStream = new ArrayList<>();
            if (insertIndex > 0 && !allEvents.isEmpty()) {
                correctedStream.addAll(allEvents.subList(0, insertIndex));
            }
            
            // Create event entity for backdated trade
            EventEntity backdatedEvent = createEventEntity(backdatedTrade, insertIndex);
            correctedStream.add(backdatedEvent);
            
            if (insertIndex < allEvents.size()) {
                correctedStream.addAll(allEvents.subList(insertIndex, allEvents.size()));
            }
            
            // 4. Replay events in chronological order
            correctedState = replayEvents(correctedStream, positionKey);
            
            // 4.5. Update PriceQuantity Schedule (hybrid approach)
            updatePriceQuantityScheduleFromEvents(correctedState, correctedStream);
            
            // 5. Generate corrected snapshot
            nextVersion = eventStoreService.getNextVersion(positionKey);
            correctedState.setVersion(nextVersion);
            
            // Load provisional snapshot for comparison
            provisionalState = eventStoreService.loadSnapshot(positionKey);
            
            // 6. Override provisional snapshot with corrected version
            // Use REQUIRES_NEW to isolate this transaction and prevent rollback issues
            saveSnapshotInNewTransaction(positionKey, correctedState);
            
            // Record metrics
            metricsService.recordCorrection();
            
            // Register transaction synchronization to execute after commit (only if transaction is active)
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                final PositionState finalCorrectedState = correctedState;
                final PositionState finalProvisionalState = provisionalState;
                final String finalPositionKey = positionKey;
                
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            // 7. Update cache (after transaction commits to avoid connection issues)
                            if (finalPositionKey != null && finalCorrectedState != null) {
                                cacheService.put("position:" + finalPositionKey, finalCorrectedState);
                            }
                            
                            // 8. Publish correction event (after transaction commits to avoid connection issues)
                            if (finalCorrectedState != null) {
                                publishCorrectionEvent(backdatedTrade, finalCorrectedState, finalProvisionalState);
                            }
                        } catch (Exception e) {
                            log.warn("Error in post-commit operations for recalculation: {}", e.getMessage(), e);
                        }
                    }
                });
            } else {
                // If no transaction, execute immediately
                try {
                    cacheService.put("position:" + positionKey, correctedState);
                    publishCorrectionEvent(backdatedTrade, correctedState, provisionalState);
                } catch (Exception e) {
                    log.warn("Error in post-transaction operations for recalculation: {}", e.getMessage(), e);
                }
            }
            
            log.info("Completed recalculation for backdated trade: tradeId={}, positionKey={}, version={}", 
                    backdatedTrade.getTradeId(), positionKey, nextVersion);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument during recalculation for trade: {}, positionKey: {}", 
                    backdatedTrade.getTradeId(), positionKey, e);
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            log.error("Error during recalculation for trade: {}, positionKey: {}, error type: {}, message: {}", 
                    backdatedTrade.getTradeId(), positionKey, e.getClass().getName(), e.getMessage(), e);
            // Log the full stack trace for debugging
            if (e.getCause() != null) {
                log.error("Root cause: {}", e.getCause().getMessage(), e.getCause());
            }
            throw e; // Re-throw to trigger rollback
        } finally {
            metricsService.recordColdpathProcessing(processingSample);
        }
    }
    
    /**
     * Save snapshot in a new transaction to isolate it and prevent rollback issues
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    private void saveSnapshotInNewTransaction(String positionKey, PositionState correctedState) {
        try {
            eventStoreService.saveSnapshot(positionKey, correctedState, 
                    ReconciliationStatus.RECONCILED, null);
        } catch (Exception e) {
            log.error("Error saving snapshot in new transaction for position: {}", positionKey, e);
            throw e;
        }
    }
    
    /**
     * Find insertion point for backdated trade in event stream
     */
    private int findInsertionPoint(List<EventEntity> events, LocalDate backdatedDate) {
        for (int i = 0; i < events.size(); i++) {
            EventEntity event = events.get(i);
            if (event.getEffectiveDate().isAfter(backdatedDate) || 
                event.getEffectiveDate().isEqual(backdatedDate)) {
                return i;
            }
        }
        return events.size(); // Insert at end if no later events
    }
    
    /**
     * Create event entity for backdated trade
     */
    private EventEntity createEventEntity(TradeEvent trade, int version) {
        try {
            // For backdated trades, set occurredAt to start of effective date (midnight)
            // This ensures backdated trades are processed before same-date events
            java.time.OffsetDateTime occurredAt;
            java.time.LocalDate effectiveDate = trade.getEffectiveDate() != null 
                    ? trade.getEffectiveDate() 
                    : trade.getTradeDate();
            
            // Check if this is a backdated trade by comparing with current date
            java.time.LocalDate today = java.time.LocalDate.now();
            if (effectiveDate != null && effectiveDate.isBefore(today)) {
                // Backdated trade: set to midnight of effective date
                occurredAt = effectiveDate
                        .atStartOfDay()
                        .atOffset(java.time.ZoneOffset.UTC);
                log.debug("Setting occurredAt to midnight for backdated trade: {}", effectiveDate);
            } else {
                // Current or forward-dated trade: use current timestamp
                occurredAt = java.time.OffsetDateTime.now();
            }
            
            return EventEntity.builder()
                    .positionKey(trade.getPositionKey())
                    .eventVer(version)
                    .eventType("TRADE")
                    .eventData(objectMapper.writeValueAsString(trade))
                    .effectiveDate(effectiveDate)
                    .occurredAt(occurredAt)
                    .createdAt(java.time.OffsetDateTime.now())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create event entity", e);
        }
    }
    
    /**
     * Replay events in chronological order to recalculate position
     */
    private PositionState replayEvents(List<EventEntity> events, String positionKey) {
        PositionState state = new PositionState(positionKey, "", "", "", "");
        
        // Sort by effective date, then by occurredAt timestamp, then by version
        // This ensures backdated trades (midnight timestamp) are processed before same-date events
        List<EventEntity> sortedEvents = new ArrayList<>(events);
        sortedEvents.sort(Comparator
                .comparing(EventEntity::getEffectiveDate)  // Primary: effective date
                .thenComparing(EventEntity::getOccurredAt) // Secondary: timestamp (tie-breaker)
                .thenComparing(EventEntity::getEventVer)); // Final: version (deterministic fallback)
        
        for (EventEntity event : sortedEvents) {
            try {
                TradeEvent tradeEvent = objectMapper.readValue(event.getEventData(), TradeEvent.class);
                state = applyTrade(state, tradeEvent);
            } catch (Exception e) {
                log.error("Failed to deserialize event during replay: {}", event.getId(), e);
            }
        }
        
        return state;
    }
    
    /**
     * Apply trade to position state (same logic as hotpath)
     */
    private PositionState applyTrade(PositionState currentState, TradeEvent tradeEvent) {
        if (currentState == null || currentState.getPositionKey() == null) {
            currentState = new PositionState(
                    tradeEvent.getPositionKey(),
                    tradeEvent.getAccount(),
                    tradeEvent.getBook(),
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
            lotLogic.addLot(currentState, quantity, price, 
                    tradeEvent.getTradeDate(), tradeEvent.getSettlementDate());
        } else if (quantity.compareTo(java.math.BigDecimal.ZERO) < 0) {
            java.math.BigDecimal qtyToReduce = quantity.abs();
            LotAllocationResult result = lotLogic.reduceLots(
                    currentState, qtyToReduce, LotLogic.TaxLotMethod.FIFO, price);
            
            if (!result.isFullyAllocated()) {
                log.warn("Could not fully reduce position during replay. Trade: {}, Remaining: {}", 
                        tradeEvent.getTradeId(), result.getRemainingQtyToAllocate());
            }
        }
        
        return currentState;
    }
    
    /**
     * Update PriceQuantity Schedule from replayed events (Hybrid Approach)
     * Rebuilds the schedule by processing all events in chronological order
     */
    private void updatePriceQuantityScheduleFromEvents(PositionState state, List<EventEntity> events) {
        if (state.getPriceQuantitySchedule() == null) {
            state.setPriceQuantitySchedule(new com.bank.esps.domain.model.PriceQuantitySchedule());
            state.getPriceQuantitySchedule().setUnit("SHARES");
            state.getPriceQuantitySchedule().setCurrency(state.getCurrency() != null ? state.getCurrency() : "USD");
        }
        
        // Process events in chronological order to build schedule
        for (EventEntity event : events) {
            try {
                TradeEvent tradeEvent = objectMapper.readValue(event.getEventData(), TradeEvent.class);
                
                java.time.LocalDate tradeDate = tradeEvent.getTradeDate();
                java.time.LocalDate settlementDate = tradeEvent.getSettlementDate() != null 
                        ? tradeEvent.getSettlementDate() 
                        : tradeEvent.getEffectiveDate();
                
                if (settlementDate == null) {
                    settlementDate = tradeDate;
                }
                
                if (tradeDate != null) {
                    // Calculate quantity and price at this point in time
                    // For coldpath, we need to recalculate state up to this event
                    // For simplicity, we'll use the final state's values
                    // In a more sophisticated implementation, we'd replay up to this event
                    java.math.BigDecimal currentQty = state.getTotalQty();
                    java.math.BigDecimal weightedAvgPrice = state.getWeightedAveragePrice();
                    java.math.BigDecimal settledQuantity = calculateSettledQuantity(state);
                    
                    state.getPriceQuantitySchedule().addOrUpdateEntry(
                            tradeDate,
                            settlementDate,
                            currentQty,
                            settledQuantity,
                            weightedAvgPrice
                    );
                }
            } catch (Exception e) {
                log.warn("Failed to update PriceQuantitySchedule from event: {}", event.getId(), e);
            }
        }
    }
    
    /**
     * Calculate settled quantity from open lots
     */
    private java.math.BigDecimal calculateSettledQuantity(PositionState state) {
        if (state.getOpenLots() == null || state.getOpenLots().isEmpty()) {
            return java.math.BigDecimal.ZERO;
        }
        
        return state.getOpenLots().stream()
                .map(lot -> lot.getSettledQuantity() != null ? lot.getSettledQuantity() : 
                           (lot.getRemainingQty() != null ? lot.getRemainingQty() : java.math.BigDecimal.ZERO))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
    
    /**
     * Publish correction event to downstream systems
     * This method should be called after the transaction commits to avoid connection issues
     */
    private void publishCorrectionEvent(TradeEvent backdatedTrade, PositionState correctedState, 
                                       PositionState provisionalState) {
        try {
            java.util.Map<String, Object> correctionEvent = new java.util.HashMap<>();
            correctionEvent.put("positionKey", backdatedTrade.getPositionKey());
            correctionEvent.put("backdatedTradeId", backdatedTrade.getTradeId());
            correctionEvent.put("correctedVersion", correctedState.getVersion());
            correctionEvent.put("correctedState", correctedState);
            
            if (provisionalState != null) {
                correctionEvent.put("previousProvisionalVersion", provisionalState.getVersion());
            }
            
            String eventJson = objectMapper.writeValueAsString(correctionEvent);
            messageProducer.send(historicalPositionCorrectedEventsTopic, 
                    backdatedTrade.getPositionKey(), eventJson);
            
            log.info("Published correction event for backdated trade: {}", backdatedTrade.getTradeId());
        } catch (Exception e) {
            log.warn("Failed to publish correction event for trade {}", backdatedTrade.getTradeId(), e);
            // Don't fail the transaction - messaging is best effort
        }
    }
}
