package com.bank.esps.application.service;

import com.bank.esps.domain.enums.EventType;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.CompressedLots;
import com.bank.esps.domain.model.ContractRules;
import com.bank.esps.domain.model.LotAllocationResult;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.TaxLot;
import com.bank.esps.domain.messaging.MessageProducer;
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
    private final MessageProducer messageProducer;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    
    public RecalculationService(
            EventStoreRepository eventStoreRepository,
            SnapshotRepository snapshotRepository,
            LotLogic lotLogic,
            ContractRulesService contractRulesService,
            IdempotencyService idempotencyService,
            MessageProducer messageProducer,
            ObjectMapper objectMapper,
            MetricsService metricsService) {
        this.eventStoreRepository = eventStoreRepository;
        this.snapshotRepository = snapshotRepository;
        this.lotLogic = lotLogic;
        this.contractRulesService = contractRulesService;
        this.idempotencyService = idempotencyService;
        this.messageProducer = messageProducer;
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
            
            // 3. Check if backdated trade event already exists (idempotency check)
            boolean backdatedEventExists = checkIfBackdatedEventExists(positionKey, backdatedTrade.getTradeId());
            if (backdatedEventExists) {
                log.info("Backdated trade event {} already exists in event store, skipping save", 
                        backdatedTrade.getTradeId());
            } else {
                // 3a. Save backdated trade event to event store
                EventEntity backdatedEvent = saveBackdatedTradeEvent(allEvents, backdatedTrade, insertionIndex);
                log.info("Saved backdated trade event {} to event store with version {}", 
                        backdatedTrade.getTradeId(), backdatedEvent.getEventVer());
                
                // Reload events to include the newly saved backdated event
                allEvents = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
                log.info("Reloaded {} events after saving backdated trade", allEvents.size());
            }
            
            // 3b. Create event stream with backdated trade injected (for replay)
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
     * Orders events chronologically by effective date for correct replay
     */
    private List<EventEntity> createReplayedEventStream(
            List<EventEntity> allEvents, 
            TradeEvent backdatedTrade, 
            int insertionIndex) {
        
        // Find the saved backdated event in the events list (it was saved with next available version)
        EventEntity savedBackdatedEvent = allEvents.stream()
                .filter(event -> {
                    try {
                        TradeEvent tradeEvent = objectMapper.readValue(event.getPayload(), TradeEvent.class);
                        return backdatedTrade.getTradeId().equals(tradeEvent.getTradeId());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
        
        if (savedBackdatedEvent == null) {
            // Fallback: if not found (shouldn't happen), create it in memory
            log.warn("Backdated event not found in loaded events, creating in-memory version");
            savedBackdatedEvent = createEventFromTrade(backdatedTrade, 
                    allEvents.stream().mapToLong(EventEntity::getEventVer).max().orElse(0L) + 1);
        }
        
        // Create list with all events (including the saved backdated event)
        List<EventEntity> allEventsWithBackdated = new ArrayList<>(allEvents);
        
        // Sort by effective date (chronological order) for replay
        // Use occurredAt as tie-breaker for same-date events to ensure proper chronological ordering
        // Backdated trades (older occurredAt) will be processed before same-date events
        allEventsWithBackdated.sort(Comparator
                .comparing(EventEntity::getEffectiveDate)
                .thenComparing(EventEntity::getOccurredAt)
                .thenComparing(EventEntity::getEventVer)); // Final tie-breaker for deterministic ordering
        
        return allEventsWithBackdated;
    }
    
    /**
     * Create EventEntity from TradeEvent
     * For backdated trades, sets occurredAt to start of effective date to ensure proper ordering
     */
    private EventEntity createEventFromTrade(TradeEvent trade, long version) {
        EventEntity event = new EventEntity();
        event.setPositionKey(trade.getPositionKey());
        event.setEventVer(version);
        event.setEventType(EventType.valueOf(trade.getTradeType()));
        event.setEffectiveDate(trade.getEffectiveDate());
        
        // For backdated trades, set occurredAt to start of effective date (midnight)
        // This ensures backdated trades are processed before same-date events
        // Use current time for current/forward-dated trades
        java.time.OffsetDateTime occurredAt;
        if (trade.getSequenceStatus() != null && 
            trade.getSequenceStatus() == com.bank.esps.domain.enums.TradeSequenceStatus.BACKDATED) {
            // Backdated trade: use start of effective date (midnight) to ensure it's processed first
            occurredAt = trade.getEffectiveDate()
                    .atStartOfDay()
                    .atOffset(java.time.ZoneOffset.UTC);
        } else {
            // Current/forward-dated trade: use current time
            occurredAt = java.time.OffsetDateTime.now();
        }
        event.setOccurredAt(occurredAt);
        
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
                        LotAllocationResult result = lotLogic.reduceLots(state, tradeEvent.getQuantity(), rules, tradeEvent.getPrice());
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
            messageProducer.publishCorrectionEvent(backdatedTrade.getPositionKey(), correctionEvent);
            
            log.info("Published correction event for position {}", backdatedTrade.getPositionKey());
            
        } catch (Exception e) {
            log.error("Error publishing correction event", e);
            // Don't fail the recalculation if event publishing fails
        }
    }
    
    /**
     * Check if backdated trade event already exists in event store (idempotency)
     */
    private boolean checkIfBackdatedEventExists(String positionKey, String tradeId) {
        List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
        return events.stream()
                .anyMatch(event -> {
                    try {
                        TradeEvent tradeEvent = objectMapper.readValue(event.getPayload(), TradeEvent.class);
                        return tradeId.equals(tradeEvent.getTradeId());
                    } catch (Exception e) {
                        log.debug("Error parsing event payload for tradeId check: {}", e.getMessage());
                        return false;
                    }
                });
    }
    
    /**
     * Save backdated trade event to event store
     * Uses the next available version number to avoid primary key conflicts
     */
    private EventEntity saveBackdatedTradeEvent(
            List<EventEntity> existingEvents, 
            TradeEvent backdatedTrade, 
            int insertionIndex) {
        
        // Get the next available version number
        Long nextVersion = existingEvents.stream()
                .map(EventEntity::getEventVer)
                .max(Long::compareTo)
                .map(max -> max + 1)
                .orElse(1L);
        
        // Create event entity for backdated trade
        EventEntity backdatedEvent = createEventFromTrade(backdatedTrade, nextVersion);
        
        // Save to event store
        eventStoreRepository.save(backdatedEvent);
        
        log.info("Saved backdated trade event {} to event store: position={}, version={}, effectiveDate={}", 
                backdatedTrade.getTradeId(), 
                backdatedTrade.getPositionKey(), 
                nextVersion, 
                backdatedTrade.getEffectiveDate());
        
        return backdatedEvent;
    }
}
