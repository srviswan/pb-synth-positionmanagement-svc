package com.bank.esps.application.service;

import com.bank.esps.domain.enums.EventType;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.*;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Hotpath position service
 * Processes current/forward-dated trades synchronously
 * Target latency: <100ms p99
 */
@Service
public class HotpathPositionService {
    
    private static final Logger log = LoggerFactory.getLogger(HotpathPositionService.class);
    
    private final EventStoreRepository eventStoreRepository;
    private final SnapshotRepository snapshotRepository;
    private final LotLogic lotLogic;
    private final ContractRulesService contractRulesService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    
    public HotpathPositionService(
            EventStoreRepository eventStoreRepository,
            SnapshotRepository snapshotRepository,
            LotLogic lotLogic,
            ContractRulesService contractRulesService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper) {
        this.eventStoreRepository = eventStoreRepository;
        this.snapshotRepository = snapshotRepository;
        this.lotLogic = lotLogic;
        this.contractRulesService = contractRulesService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Process current-dated or forward-dated trade
     * Synchronous processing with optimistic locking
     */
    @Transactional
    @Retryable(
            retryFor = {DataIntegrityViolationException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    public void processCurrentDatedTrade(TradeEvent tradeEvent) {
        String positionKey = tradeEvent.getPositionKey();
        
        // 1. Load snapshot with optimistic locking
        SnapshotEntity snapshot = snapshotRepository.findById(positionKey)
                .orElse(createNewSnapshot(positionKey, tradeEvent));
        
        long expectedVersion = snapshot.getLastVer() + 1;
        
        // 2. Apply business logic
        PositionState state = inflateSnapshot(snapshot);
        ContractRules rules = contractRulesService.getContractRules(tradeEvent.getContractId());
        LotAllocationResult result;
        
        if (tradeEvent.isIncrease()) {
            result = lotLogic.addLot(state, tradeEvent.getQuantity(), 
                    tradeEvent.getPrice(), tradeEvent.getEffectiveDate());
        } else {
            result = lotLogic.reduceLots(state, tradeEvent.getQuantity(), rules);
        }
        
        // 3. Persist event (source of truth)
        EventEntity event = createEventEntity(tradeEvent, expectedVersion, result);
        
        try {
            eventStoreRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            // Concurrency detected: retry
            log.warn("Version conflict for position {}, retrying", positionKey);
            throw e;
        }
        
        // 4. Update snapshot (cache)
        updateSnapshot(snapshot, state, expectedVersion, ReconciliationStatus.RECONCILED);
        snapshotRepository.save(snapshot);
        
        // 5. Mark as processed
        idempotencyService.markAsProcessed(tradeEvent, expectedVersion);
        
        log.debug("Successfully processed trade {} for position {}, version {}", 
                tradeEvent.getTradeId(), positionKey, expectedVersion);
    }
    
    /**
     * Create new snapshot for new position
     */
    private SnapshotEntity createNewSnapshot(String positionKey, TradeEvent tradeEvent) {
        PositionState initialState = new PositionState();
        CompressedLots emptyCompressed = CompressedLots.compress(initialState.getOpenLots());
        
        SnapshotEntity snapshot = new SnapshotEntity();
        snapshot.setPositionKey(positionKey);
        snapshot.setLastVer(0L);
        snapshot.setUti(tradeEvent.getTradeId()); // Initial UTI
        snapshot.setStatus(com.bank.esps.domain.enums.PositionStatus.ACTIVE);
        snapshot.setReconciliationStatus(ReconciliationStatus.RECONCILED);
        snapshot.setTaxLotsCompressed(toJson(emptyCompressed));
        snapshot.setSummaryMetrics(toJson(createSummaryMetrics(initialState)));
        snapshot.setVersion(0L);
        return snapshot;
    }
    
    /**
     * Inflate snapshot to PositionState
     */
    private PositionState inflateSnapshot(SnapshotEntity snapshot) {
        try {
            if (snapshot.getTaxLotsCompressed() == null || snapshot.getTaxLotsCompressed().trim().isEmpty()) {
                return new PositionState();
            }
            CompressedLots compressed = objectMapper.readValue(
                    snapshot.getTaxLotsCompressed(), CompressedLots.class);
            PositionState state = new PositionState();
            state.setOpenLots(compressed.inflate());
            return state;
        } catch (Exception e) {
            log.error("Error inflating snapshot for position {}", snapshot.getPositionKey(), e);
            // Return empty state on error
            return new PositionState();
        }
    }
    
    /**
     * Create event entity
     */
    private EventEntity createEventEntity(TradeEvent trade, long version, LotAllocationResult result) {
        try {
            EventEntity event = new EventEntity();
            event.setPositionKey(trade.getPositionKey());
            event.setEventVer(version);
            event.setEventType(EventType.valueOf(trade.getTradeType()));
            event.setEffectiveDate(trade.getEffectiveDate());
            event.setOccurredAt(OffsetDateTime.now());
            event.setPayload(toJson(trade));
            event.setMetaLots(toJson(result.getAllocationsMap()));
            event.setCorrelationId(trade.getCorrelationId());
            event.setCausationId(trade.getCausationId());
            event.setContractId(trade.getContractId());
            event.setUserId(trade.getUserId());
            return event;
        } catch (Exception e) {
            log.error("Error creating event entity", e);
            throw new RuntimeException("Failed to create event entity", e);
        }
    }
    
    /**
     * Update snapshot from position state
     */
    private void updateSnapshot(SnapshotEntity snapshot, PositionState state, 
                               long version, ReconciliationStatus reconciliationStatus) {
        CompressedLots compressed = CompressedLots.compress(state.getOpenLots());
        snapshot.setLastVer(version);
        snapshot.setTaxLotsCompressed(toJson(compressed));
        snapshot.setSummaryMetrics(toJson(createSummaryMetrics(state)));
        snapshot.setReconciliationStatus(reconciliationStatus);
    }
    
    /**
     * Create summary metrics
     */
    private java.util.Map<String, Object> createSummaryMetrics(PositionState state) {
        return java.util.Map.of(
                "net_qty", state.getTotalQty(),
                "exposure", state.getExposure(),
                "lot_count", state.getLotCount()
        );
    }
    
    /**
     * Convert object to JSON string
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Error serializing to JSON", e);
            return "{}";
        }
    }
}
