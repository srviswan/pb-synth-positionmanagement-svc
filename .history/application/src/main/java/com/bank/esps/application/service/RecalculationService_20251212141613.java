package com.bank.esps.application.service;

import com.bank.esps.domain.enums.EventType;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.CompressedLots;
import com.bank.esps.domain.model.ContractRules;
import com.bank.esps.domain.model.LotAllocationResult;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.TaxLot;
import com.bank.esps.infrastructure.kafka.TradeEventProducer;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Coldpath Recalculation Service
 * Handles backdated trade processing with full event stream replay
 */
@Service
public class RecalculationService {
    
    private static final Logger log = LoggerFactory.getLogger(RecalculationService.class);
    
    private final EventStoreRepository eventStoreRepository;
    private final SnapshotRepository snapshotRepository;
    private final LotLogic lotLogic;
    private final ContractRulesService contractRulesService;
    private final IdempotencyService idempotencyService;
    private final TradeEventProducer eventProducer;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    
    public RecalculationService(
            EventStoreRepository eventStoreRepository,
            SnapshotRepository snapshotRepository,
            LotLogic lotLogic,
            ContractRulesService contractRulesService,
            IdempotencyService idempotencyService,
            TradeEventProducer eventProducer,
            ObjectMapper objectMapper,
            MetricsService metricsService) {
        this.eventStoreRepository = eventStoreRepository;
        this.snapshotRepository = snapshotRepository;
        this.lotLogic = lotLogic;
        this.contractRulesService = contractRulesService;
        this.idempotencyService = idempotencyService;
        this.eventProducer = eventProducer;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }
    
    /**
     * Recalculate position with backdated trade injected
     * This is the main coldpath processing method
     */
    @Transactional
    public void recalculatePosition(TradeEvent backdatedTrade) {
        String positionKey = backdatedTrade.getPositionKey();
        log.info("Starting recalculation for backdated trade {} on position {}", 
                backdatedTrade.getTradeId(), positionKey);
        
        var sample = metricsService.startColdpathProcessing();
        try {
            // 1. Load complete event stream for position
            List<EventEntity> allEvents = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
            log.info("Loaded {} events for position {}", allEvents.size(), positionKey);
            
            // 2. Find insertion point for backdated trade
            LocalDate backdatedDate = backdatedTrade.getEffectiveDate();
            int insertionIndex = findInsertionPoint(allEvents, backdatedDate);
            log.info("Insertion point for backdated trade: index {}, effective date {}", 
                    insertionIndex, backdatedDate);
            
            // 3. Create event stream with backdated trade injected
            List<EventEntity> replayedEvents = createReplayedEventStream(allEvents, backdatedTrade, insertionIndex);
            log.info("Created replayed event stream with {} events (including backdated trade)", 
                    replayedEvents.size());
            
            // 4. Replay events chronologically and recalculate tax lots
            PositionState recalculatedState = replayEvents(replayedEvents);
            log.info("Recalculated position state: {} open lots, total qty: {}", 
                    recalculatedState.getOpenLots().size(), recalculatedState.getTotalQty());
            
            // 5. Load current snapshot to compare
            Optional<SnapshotEntity> currentSnapshotOpt = snapshotRepository.findById(positionKey);
            if (currentSnapshotOpt.isEmpty()) {
                log.warn("No snapshot found for position {}, creating new one", positionKey);
                return;
            }
            
            SnapshotEntity currentSnapshot = currentSnapshotOpt.get();
            PositionState currentState = inflateSnapshot(currentSnapshot);
            
            // 6. Calculate deltas
            BigDecimal qtyDelta = recalculatedState.getTotalQty().subtract(currentState.getTotalQty());
            BigDecimal exposureDelta = recalculatedState.getExposure().subtract(currentState.getExposure());
            int lotCountDelta = recalculatedState.getLotCount() - currentState.getLotCount();
            
            log.info("Recalculation deltas - Qty: {}, Exposure: {}, Lot Count: {}", 
                    qtyDelta, exposureDelta, lotCountDelta);
            
            // 7. Generate corrected snapshot
            SnapshotEntity correctedSnapshot = createCorrectedSnapshot(
                    positionKey, 
                    recalculatedState, 
                    replayedEvents.size(),
                    currentSnapshot);
            
            // 8. Override provisional snapshot with corrected version
            snapshotRepository.save(correctedSnapshot);
            log.info("Saved corrected snapshot for position {}, version {}", 
                    positionKey, correctedSnapshot.getLastVer());
            
            // 9. Mark trade as processed
            idempotencyService.markAsProcessed(backdatedTrade, (long) replayedEvents.size());
            
            // 10. Publish correction event
            publishCorrectionEvent(backdatedTrade, currentSnapshot, correctedSnapshot, 
                    qtyDelta, exposureDelta, lotCountDelta);
            
            log.info("Successfully recalculated position {} with backdated trade {}", 
                    positionKey, backdatedTrade.getTradeId());
            
            metricsService.incrementTradesProcessedColdpath();
            metricsService.recordProvisionalToReconciled();
            
        } catch (Exception e) {
            log.error("Error recalculating position {} for backdated trade {}", 
                    positionKey, backdatedTrade.getTradeId(), e);
            metricsService.incrementErrors();
            idempotencyService.markAsFailed(backdatedTrade, e.getMessage());
            throw new RuntimeException("Failed to recalculate position", e);
        } finally {
            metricsService.recordColdpathProcessing(sample);
        }
    }
    
    /**
     * Find insertion point for backdated trade based on effective date
     */
    private int findInsertionPoint(List<EventEntity> events, LocalDate backdatedDate) {
        for (int i = 0; i < events.size(); i++) {
            EventEntity event = events.get(i);
            if (event.getEffectiveDate().isAfter(backdatedDate) || 
                event.getEffectiveDate().isEqual(backdatedDate)) {
                return i;
            }
        }
        return events.size(); // Insert at end if all events are before backdated date
    }
    
    /**
     * Create replayed event stream with backdated trade injected
     */
    private List<EventEntity> createReplayedEventStream(
            List<EventEntity> originalEvents, 
            TradeEvent backdatedTrade, 
            int insertionIndex) {
        
        List<EventEntity> replayedEvents = new ArrayList<>();
        
        // Add events before insertion point
        for (int i = 0; i < insertionIndex; i++) {
            replayedEvents.add(originalEvents.get(i));
        }
        
        // Add backdated trade as new event
        EventEntity backdatedEvent = createEventFromTrade(backdatedTrade, insertionIndex + 1);
        replayedEvents.add(backdatedEvent);
        
        // Add remaining events (with renumbered versions)
        for (int i = insertionIndex; i < originalEvents.size(); i++) {
            EventEntity originalEvent = originalEvents.get(i);
            EventEntity replayedEvent = new EventEntity();
            replayedEvent.setPositionKey(originalEvent.getPositionKey());
            replayedEvent.setEventVer((long) (i + 2)); // +2 because we inserted one event
            replayedEvent.setEventType(originalEvent.getEventType());
            replayedEvent.setEffectiveDate(originalEvent.getEffectiveDate());
            replayedEvent.setOccurredAt(originalEvent.getOccurredAt());
            replayedEvent.setPayload(originalEvent.getPayload());
            replayedEvent.setCorrelationId(originalEvent.getCorrelationId());
            replayedEvent.setContractId(originalEvent.getContractId());
            replayedEvents.add(replayedEvent);
        }
        
        return replayedEvents;
    }
    
    /**
     * Create EventEntity from TradeEvent
     */
    private EventEntity createEventFromTrade(TradeEvent trade, long version) {
        EventEntity event = new EventEntity();
        event.setPositionKey(trade.getPositionKey());
        event.setEventVer(version);
        event.setEventType(EventType.valueOf(trade.getTradeType()));
        event.setEffectiveDate(trade.getEffectiveDate());
        event.setOccurredAt(java.time.OffsetDateTime.now());
        event.setCorrelationId(trade.getCorrelationId());
        event.setContractId(trade.getContractId());
        
        try {
            event.setPayload(objectMapper.writeValueAsString(trade));
        } catch (Exception e) {
            log.error("Error serializing trade to JSON", e);
            throw new RuntimeException("Failed to serialize trade", e);
        }
        
        return event;
    }
    
    /**
     * Replay events chronologically and recalculate tax lots
     */
    private PositionState replayEvents(List<EventEntity> events) {
        PositionState state = new PositionState();
        
        for (EventEntity event : events) {
            try {
                TradeEvent tradeEvent = objectMapper.readValue(event.getPayload(), TradeEvent.class);
                
                // Get contract rules
                ContractRules rules = contractRulesService.getContractRules(
                        tradeEvent.getContractId());
                
                // Apply trade based on type
                switch (event.getEventType()) {
                    case NEW_TRADE, INCREASE -> {
                        LotAllocationResult result = lotLogic.addLot(
                                state, 
                                tradeEvent.getQuantity(), 
                                tradeEvent.getPrice(), 
                                tradeEvent.getEffectiveDate());
                        log.debug("Added lot from event {}: {}", event.getEventVer(), result);
                    }
                    case DECREASE -> {
                        LotAllocationResult result = lotLogic.reduceLots(state, tradeEvent.getQuantity(), rules);
                        log.debug("Reduced lots from event {}: {}", event.getEventVer(), result);
                    }
                    default -> {
                        log.warn("Unhandled event type: {}", event.getEventType());
                    }
                }
            } catch (Exception e) {
                log.error("Error replaying event {}: {}", event.getEventVer(), e.getMessage(), e);
                throw new RuntimeException("Failed to replay event", e);
            }
        }
        
        return state;
    }
    
    /**
     * Inflate snapshot to PositionState
     */
    private PositionState inflateSnapshot(SnapshotEntity snapshot) {
        if (snapshot.getTaxLotsCompressed() == null || snapshot.getTaxLotsCompressed().trim().isEmpty()) {
            return new PositionState();
        }
        
        try {
            CompressedLots compressed = objectMapper.readValue(
                    snapshot.getTaxLotsCompressed(), 
                    CompressedLots.class);
            List<TaxLot> lots = compressed.inflate();
            
            PositionState state = new PositionState();
            state.setOpenLots(lots);
            return state;
        } catch (Exception e) {
            log.error("Error inflating snapshot for position {}", snapshot.getPositionKey(), e);
            throw new RuntimeException("Failed to inflate snapshot", e);
        }
    }
    
    /**
     * Create corrected snapshot
     */
    private SnapshotEntity createCorrectedSnapshot(
            String positionKey,
            PositionState state,
            long newVersion,
            SnapshotEntity currentSnapshot) {
        
        SnapshotEntity snapshot = new SnapshotEntity();
        snapshot.setPositionKey(positionKey);
        snapshot.setLastVer(newVersion);
        snapshot.setUti(currentSnapshot.getUti());
        snapshot.setStatus(currentSnapshot.getStatus());
        snapshot.setReconciliationStatus(ReconciliationStatus.RECONCILED); // Mark as reconciled
        snapshot.setVersion(currentSnapshot.getVersion() + 1); // Optimistic locking version
        
        // Compress lots
        try {
            CompressedLots compressed = CompressedLots.compress(state.getAllLots());
            snapshot.setTaxLotsCompressed(objectMapper.writeValueAsString(compressed));
        } catch (Exception e) {
            log.error("Error compressing lots for corrected snapshot", e);
            throw new RuntimeException("Failed to compress lots", e);
        }
        
        // Update summary metrics
        snapshot.setSummaryMetrics(String.format(
                "{\"totalQty\":%s,\"exposure\":%s,\"lotCount\":%d}",
                state.getTotalQty(), state.getExposure(), state.getLotCount()));
        
        snapshot.setLastUpdatedAt(java.time.OffsetDateTime.now());
        
        return snapshot;
    }
    
    /**
     * Publish correction event to downstream systems
     */
    private void publishCorrectionEvent(
            TradeEvent backdatedTrade,
            SnapshotEntity previousSnapshot,
            SnapshotEntity correctedSnapshot,
            BigDecimal qtyDelta,
            BigDecimal exposureDelta,
            int lotCountDelta) {
        
        try {
            // Create correction event payload
            String correctionEvent = String.format(
                    "{\"type\":\"HISTORICAL_POSITION_CORRECTED\"," +
                    "\"positionKey\":\"%s\"," +
                    "\"backdatedTradeId\":\"%s\"," +
                    "\"previousVersion\":%d," +
                    "\"correctedVersion\":%d," +
                    "\"qtyDelta\":%s," +
                    "\"exposureDelta\":%s," +
                    "\"lotCountDelta\":%d," +
                    "\"correctedAt\":\"%s\"}",
                    backdatedTrade.getPositionKey(),
                    backdatedTrade.getTradeId(),
                    previousSnapshot.getLastVer(),
                    correctedSnapshot.getLastVer(),
                    qtyDelta,
                    exposureDelta,
                    lotCountDelta,
                    java.time.OffsetDateTime.now());
            
            // Publish to correction events topic
            eventProducer.publishCorrectionEvent(backdatedTrade.getPositionKey(), correctionEvent);
            
            log.info("Published correction event for position {}", backdatedTrade.getPositionKey());
            
        } catch (Exception e) {
            log.error("Error publishing correction event", e);
            // Don't fail the recalculation if event publishing fails
        }
    }
}
