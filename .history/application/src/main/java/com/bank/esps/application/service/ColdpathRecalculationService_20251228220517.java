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
import org.springframework.transaction.annotation.Transactional;

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
     * Kafka listener for backdated trades
     */
    @KafkaListener(topics = "${app.kafka.topics.backdated-trades:backdated-trades}", 
                   groupId = "${spring.kafka.consumer.group-id:position-management-coldpath}")
    @Transactional
    public void processBackdatedTrade(String tradeJson) {
        try {
            TradeEvent backdatedTrade = objectMapper.readValue(tradeJson, TradeEvent.class);
            log.info("Processing backdated trade in coldpath: tradeId={}, positionKey={}, effectiveDate={}", 
                    backdatedTrade.getTradeId(), backdatedTrade.getPositionKey(), backdatedTrade.getEffectiveDate());
            
            recalculatePosition(backdatedTrade);
            
        } catch (Exception e) {
            log.error("Failed to process backdated trade: {}", tradeJson, e);
            throw new RuntimeException("Failed to process backdated trade", e);
        }
    }
    
    /**
     * Recalculate position with backdated trade injected at correct chronological position
     */
    @Transactional
    public void recalculatePosition(TradeEvent backdatedTrade) {
        io.micrometer.core.instrument.Timer.Sample processingSample = metricsService.startColdpathProcessing();
        try {
            String positionKey = backdatedTrade.getPositionKey();
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
        correctedStream.addAll(allEvents.subList(0, insertIndex));
        
        // Create event entity for backdated trade
        EventEntity backdatedEvent = createEventEntity(backdatedTrade, insertIndex);
        correctedStream.add(backdatedEvent);
        
        if (insertIndex < allEvents.size()) {
            correctedStream.addAll(allEvents.subList(insertIndex, allEvents.size()));
        }
        
        // 4. Replay events in chronological order
        PositionState correctedState = replayEvents(correctedStream, positionKey);
        
        // 5. Generate corrected snapshot
        int nextVersion = eventStoreService.getNextVersion(positionKey);
        correctedState.setVersion(nextVersion);
        
        // Load provisional snapshot for comparison
        PositionState provisionalState = eventStoreService.loadSnapshot(positionKey);
        
        // 6. Override provisional snapshot with corrected version
        eventStoreService.saveSnapshot(positionKey, correctedState, 
                ReconciliationStatus.RECONCILED, null);
        
        // 7. Update cache
        cacheService.put("position:" + positionKey, correctedState);
        
        // 8. Publish correction event
        publishCorrectionEvent(backdatedTrade, correctedState, provisionalState);
        
            // Record metrics
            metricsService.recordCorrection();
            
            log.info("Completed recalculation for backdated trade: tradeId={}, positionKey={}, version={}", 
                    backdatedTrade.getTradeId(), positionKey, nextVersion);
        } finally {
            metricsService.recordColdpathProcessing(processingSample);
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
            return EventEntity.builder()
                    .positionKey(trade.getPositionKey())
                    .eventVer(version)
                    .eventType("TRADE")
                    .eventData(objectMapper.writeValueAsString(trade))
                    .effectiveDate(trade.getEffectiveDate() != null 
                            ? trade.getEffectiveDate() 
                            : trade.getTradeDate())
                    .occurredAt(java.time.OffsetDateTime.now())
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
        PositionState state = new PositionState(positionKey, "", "", "");
        
        // Sort by effective date, then by version
        List<EventEntity> sortedEvents = new ArrayList<>(events);
        sortedEvents.sort(Comparator
                .comparing(EventEntity::getEffectiveDate)
                .thenComparing(EventEntity::getEventVer));
        
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
     * Publish correction event to downstream systems
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
            // Don't fail the transaction
        }
    }
}
