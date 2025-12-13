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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Ensure EntityManager is injected (may be null in some test contexts)
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
    
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
        // Configure ObjectMapper to ignore unknown properties (critical for CompressedLots deserialization)
        // The ObjectMapper from KafkaConfig should already have this configured, but ensure it's set
        if (objectMapper.getDeserializationConfig().isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
            this.objectMapper = objectMapper.copy();
            this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        } else {
            this.objectMapper = objectMapper;
        }
    }
    
    /**
     * Process current-dated or forward-dated trade
     * Synchronous processing with optimistic locking
     */
    @Transactional
    @Retryable(
            retryFor = {DataIntegrityViolationException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2),
            noRetryFor = {RuntimeException.class, IllegalStateException.class} // Don't retry on deserialization errors
    )
    public void processCurrentDatedTrade(TradeEvent tradeEvent) {
        String positionKey = tradeEvent.getPositionKey();
        
        // 1. Load snapshot with optimistic locking
        // Clear persistence context BEFORE querying to ensure fresh data from database
        // This is critical to avoid stale cached data between transactions
        if (entityManager != null) {
            entityManager.clear();
        }
        
        // Always query fresh from database to avoid stale cache
        // Use findById which will query the database directly
        Optional<SnapshotEntity> snapshotOpt = snapshotRepository.findById(positionKey);
        SnapshotEntity snapshot;
        
        if (snapshotOpt.isPresent()) {
            snapshot = snapshotOpt.get();
            
            // CRITICAL: Refresh the entity from database to ensure we have the latest data
            // This is especially important when the previous transaction just committed
            if (entityManager != null) {
                try {
                    entityManager.refresh(snapshot);
                    log.info("Refreshed snapshot from database for position {}", positionKey);
                } catch (Exception e) {
                    log.warn("Could not refresh snapshot (may not be managed): {}", e.getMessage());
                    // If refresh fails, the entity might not be managed, so reload it
                    snapshot = snapshotRepository.findById(positionKey).orElse(snapshot);
                }
            }
            
            // Log snapshot state for debugging
            String compressedData = snapshot.getTaxLotsCompressed();
            log.info("Loaded snapshot for position {}: version {}, taxLotsCompressed length: {}, data preview: {}", 
                    positionKey, snapshot.getLastVer(), 
                    compressedData != null ? compressedData.length() : 0,
                    compressedData != null && compressedData.length() > 0 ? 
                        compressedData.substring(0, Math.min(100, compressedData.length())) : "null");
        } else {
            snapshot = createNewSnapshot(positionKey, tradeEvent);
            snapshot = snapshotRepository.save(snapshot);
            log.info("Created new snapshot for position {}", positionKey);
        }
        
        long expectedVersion = snapshot.getLastVer() + 1;
        
        // 2. Apply business logic
        PositionState state = inflateSnapshot(snapshot);
        
        // Debug: Log state before processing
        int lotCount = state.getOpenLots().size();
        int allLotsCount = state.getAllLots().size();
        BigDecimal totalQty = state.getTotalQty();
        log.info("Processing trade {} ({}) for position {}, current state has {} open lots ({} total), total qty: {}, snapshot version: {}", 
                tradeEvent.getTradeId(), tradeEvent.getTradeType(), positionKey, lotCount, allLotsCount, totalQty, snapshot.getLastVer());
        
        // Log lot details for debugging
        if (allLotsCount > 0) {
            log.info("Lot details: {}", state.getAllLots().stream()
                    .map(lot -> String.format("Lot %s: qty=%s, closed=%s", lot.getId(), lot.getRemainingQty(), lot.isClosed()))
                    .collect(java.util.stream.Collectors.joining(", ")));
        }
        
        // Validate state before processing DECREASE
        if (tradeEvent.isDecrease() && lotCount == 0) {
            log.error("CRITICAL: Attempting DECREASE on position {} with no open lots. Snapshot version: {}, all lots: {}, taxLotsCompressed length: {}", 
                    positionKey, snapshot.getLastVer(), allLotsCount,
                    snapshot.getTaxLotsCompressed() != null ? snapshot.getTaxLotsCompressed().length() : 0);
            if (snapshot.getTaxLotsCompressed() != null && snapshot.getTaxLotsCompressed().length() > 0) {
                log.error("Tax lots compressed data (first 200 chars): {}", 
                        snapshot.getTaxLotsCompressed().substring(0, Math.min(200, snapshot.getTaxLotsCompressed().length())));
                
                // Try to manually parse and see what's in the snapshot
                try {
                    ObjectMapper debugMapper = new ObjectMapper();
                    debugMapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                    com.fasterxml.jackson.databind.JsonNode debugNode = debugMapper.readTree(snapshot.getTaxLotsCompressed());
                    log.error("Debug: Snapshot JSON has {} ids, {} qtys", 
                            debugNode.has("ids") ? debugNode.get("ids").size() : 0,
                            debugNode.has("qtys") ? debugNode.get("qtys").size() : 0);
                    if (debugNode.has("qtys") && debugNode.get("qtys").isArray()) {
                        List<String> qtys = new java.util.ArrayList<>();
                        for (com.fasterxml.jackson.databind.JsonNode qtyNode : debugNode.get("qtys")) {
                            qtys.add(qtyNode.asText());
                        }
                        log.error("Debug: Quantities in snapshot: {}", String.join(", ", qtys));
                    }
                } catch (Exception e) {
                    log.error("Error parsing snapshot for debug", e);
                }
            }
            
            // Check if lots are being filtered out incorrectly
            if (allLotsCount > 0) {
                log.error("CRITICAL: getAllLots() has {} lots but getOpenLots() returned 0. All lots may be incorrectly marked as closed!", allLotsCount);
                log.error("All lots details: {}", state.getAllLots().stream()
                        .map(lot -> String.format("Lot %s: qty=%s, closed=%s, isClosed()=%s", 
                                lot.getId(), lot.getRemainingQty(), lot.isClosed(), lot.isClosed()))
                        .collect(java.util.stream.Collectors.joining(", ")));
            }
            
            throw new IllegalStateException("Cannot process DECREASE: position has no open lots. Expected lots from previous trades.");
        }
        ContractRules rules = contractRulesService.getContractRules(tradeEvent.getContractId());
        LotAllocationResult result;
        
        if (tradeEvent.isIncrease()) {
            result = lotLogic.addLot(state, tradeEvent.getQuantity(), 
                    tradeEvent.getPrice(), tradeEvent.getEffectiveDate());
            log.debug("After adding lot, state now has {} open lots ({} total), total qty: {}", 
                    state.getOpenLots().size(), state.getAllLots().size(), state.getTotalQty());
        } else {
            result = lotLogic.reduceLots(state, tradeEvent.getQuantity(), rules);
            log.debug("After reducing lots, state now has {} open lots ({} total), total qty: {}", 
                    state.getOpenLots().size(), state.getAllLots().size(), state.getTotalQty());
        }
        
        // Verify state has lots before saving (especially for INCREASE after NEW_TRADE)
        int lotsBeforeSave = state.getAllLots().size();
        if (lotsBeforeSave == 0 && tradeEvent.isIncrease()) {
            log.error("ERROR: State has 0 lots after adding lot for trade {}. This should not happen!", tradeEvent.getTradeId());
        }
        
        // 3. Persist event (source of truth)
        EventEntity event = createEventEntity(tradeEvent, expectedVersion, result);
        
        try {
            EventEntity savedEvent = eventStoreRepository.save(event);
            
            // Flush immediately to ensure event is persisted before snapshot update
            if (entityManager != null) {
                entityManager.flush();
            }
            
            log.info("✅ Saved event for position {}: version {}, type {}, effectiveDate {}, payload length: {}", 
                    positionKey, savedEvent.getEventVer(), savedEvent.getEventType(), 
                    savedEvent.getEffectiveDate(), 
                    savedEvent.getPayload() != null ? savedEvent.getPayload().length() : 0);
            
            // Verify event was actually saved by querying it back
            if (entityManager != null) {
                entityManager.clear(); // Clear to force fresh query
                EventEntity verifyEvent = eventStoreRepository.findById(
                    new EventEntity.EventEntityId(positionKey, expectedVersion)
                ).orElse(null);
                if (verifyEvent == null) {
                    log.error("❌ CRITICAL: Event was saved but cannot be retrieved! Position: {}, Version: {}", 
                            positionKey, expectedVersion);
                } else {
                    log.debug("✅ Verified event exists in database: position {}, version {}", positionKey, expectedVersion);
                }
            }
        } catch (DataIntegrityViolationException e) {
            // Concurrency detected: retry
            log.warn("Version conflict for position {}, retrying", positionKey);
            throw e;
        } catch (Exception e) {
            log.error("Error saving event for position {}: {}", positionKey, e.getMessage(), e);
            throw new RuntimeException("Failed to save event for position " + positionKey, e);
        }
        
        // 4. Update snapshot (cache) - ensure we save all lots
        updateSnapshot(snapshot, state, expectedVersion, ReconciliationStatus.RECONCILED);
        
        // Verify snapshot has data before saving
        if (snapshot.getTaxLotsCompressed() == null || snapshot.getTaxLotsCompressed().trim().isEmpty()) {
            log.error("ERROR: Snapshot taxLotsCompressed is empty before save for trade {}! State had {} lots.", 
                    tradeEvent.getTradeId(), lotsBeforeSave);
        }
        
        // Save and flush to ensure immediate persistence
        SnapshotEntity savedSnapshot = snapshotRepository.saveAndFlush(snapshot);
        
        // Verify the saved snapshot has data
        if (savedSnapshot.getTaxLotsCompressed() == null || savedSnapshot.getTaxLotsCompressed().trim().isEmpty()) {
            log.error("CRITICAL ERROR: Snapshot taxLotsCompressed is empty after save for trade {}! State had {} lots before save.", 
                    tradeEvent.getTradeId(), lotsBeforeSave);
            throw new IllegalStateException("Failed to save snapshot: taxLotsCompressed is empty after save");
        }
        
        log.info("Successfully saved snapshot for position {}: version {}, compressed data length: {}, preview: {}", 
                positionKey, savedSnapshot.getLastVer(), savedSnapshot.getTaxLotsCompressed().length(),
                savedSnapshot.getTaxLotsCompressed().substring(0, Math.min(150, savedSnapshot.getTaxLotsCompressed().length())));
        
        // Force flush to database to ensure data is committed
        if (entityManager != null) {
            entityManager.flush();
        }
        
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
        log.info("ENTERING inflateSnapshot for position {}, version {}", snapshot.getPositionKey(), snapshot.getLastVer());
        try {
            if (snapshot.getTaxLotsCompressed() == null || snapshot.getTaxLotsCompressed().trim().isEmpty()) {
                log.debug("Snapshot for position {} has empty tax lots compressed, returning empty state", snapshot.getPositionKey());
                return new PositionState();
            }
            
            String compressedJson = snapshot.getTaxLotsCompressed();
            log.info("Inflating snapshot for position {}, compressed JSON length: {}, first 100 chars: {}", 
                    snapshot.getPositionKey(), compressedJson.length(),
                    compressedJson.length() > 0 ? compressedJson.substring(0, Math.min(100, compressedJson.length())) : "empty");
            
            // Deserialize with explicit configuration to ignore unknown properties
            // Always use a fresh ObjectMapper configured to ignore unknown properties
            // This ensures the "empty" field in JSON doesn't cause deserialization to fail
            ObjectMapper deserializingMapper = new ObjectMapper();
            // CRITICAL: Disable FAIL_ON_UNKNOWN_PROPERTIES to ignore "empty" field
            deserializingMapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            deserializingMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            
            // Parse JSON and manually construct CompressedLots to avoid "empty" field issue
            // readTree doesn't fail on unknown properties, so this should work
            log.info("Starting manual JSON parsing for position {}", snapshot.getPositionKey());
            
            // Wrap readTree in try-catch to see if it's throwing the exception
            com.fasterxml.jackson.databind.JsonNode jsonNode;
            try {
                jsonNode = deserializingMapper.readTree(compressedJson);
                log.info("Successfully parsed JSON tree, now constructing CompressedLots manually");
            } catch (Exception readTreeException) {
                log.error("CRITICAL: readTree threw exception! This should not happen. Exception: {}", readTreeException.getMessage(), readTreeException);
                throw new RuntimeException("readTree failed for position " + snapshot.getPositionKey(), readTreeException);
            }
            CompressedLots compressed = new CompressedLots();
            if (jsonNode.has("ids") && jsonNode.get("ids").isArray()) {
                List<String> ids = new java.util.ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode idNode : jsonNode.get("ids")) {
                    ids.add(idNode.asText());
                }
                compressed.setIds(ids);
            }
            if (jsonNode.has("dates") && jsonNode.get("dates").isArray()) {
                List<String> dates = new java.util.ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode dateNode : jsonNode.get("dates")) {
                    dates.add(dateNode.asText());
                }
                compressed.setDates(dates);
            }
            if (jsonNode.has("prices") && jsonNode.get("prices").isArray()) {
                List<BigDecimal> prices = new java.util.ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode priceNode : jsonNode.get("prices")) {
                    prices.add(priceNode.decimalValue());
                }
                compressed.setPrices(prices);
            }
            if (jsonNode.has("qtys") && jsonNode.get("qtys").isArray()) {
                List<BigDecimal> qtys = new java.util.ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode qtyNode : jsonNode.get("qtys")) {
                    qtys.add(qtyNode.decimalValue());
                }
                compressed.setQtys(qtys);
            }
            // Ignore "empty" field - it's not needed
            log.info("Manually constructed CompressedLots with {} ids, {} dates, {} prices, {} qtys", 
                    compressed.getIds().size(), compressed.getDates().size(), 
                    compressed.getPrices().size(), compressed.getQtys().size());
            List<TaxLot> inflatedLots = compressed.inflate();
            log.info("Successfully inflated {} lots from manually constructed CompressedLots. Lot details: {}", 
                    inflatedLots.size(),
                    inflatedLots.stream()
                            .map(lot -> String.format("Lot %s: qty=%s, closed=%s", lot.getId(), lot.getRemainingQty(), lot.isClosed()))
                            .collect(java.util.stream.Collectors.joining(", ")));
            
            PositionState state = new PositionState();
            // CRITICAL: setOpenLots() sets the raw field directly
            // The raw field is what getAllLots() reads from
            // getOpenLots() filters the raw field to exclude closed lots
            state.setOpenLots(inflatedLots);
            
            // Verify the lots were set correctly
            if (inflatedLots.size() > 0) {
                int rawLotsCount = state.getAllLots().size();
                if (rawLotsCount != inflatedLots.size()) {
                    log.error("MISMATCH: Inflated {} lots but getAllLots() returned {}. This indicates a problem!", 
                            inflatedLots.size(), rawLotsCount);
                }
                
                // Verify lots aren't being incorrectly marked as closed
                int closedCount = (int) inflatedLots.stream().filter(TaxLot::isClosed).count();
                if (closedCount > 0) {
                    log.warn("Warning: {} out of {} inflated lots are marked as closed. This might be incorrect.", 
                            closedCount, inflatedLots.size());
                }
            }
            
            int openLotsCount = state.getOpenLots().size();
            BigDecimal totalQty = state.getTotalQty();
            log.info("Successfully inflated {} lots from snapshot for position {}: {} open lots, {} total lots, total qty: {}", 
                    inflatedLots.size(), snapshot.getPositionKey(), openLotsCount, state.getAllLots().size(), totalQty);
            
            // Log lot details for debugging
            if (inflatedLots.size() > 0) {
                log.info("Inflated lot details: {}", inflatedLots.stream()
                        .map(lot -> String.format("Lot %s: qty=%s, closed=%s, isClosed()=%s", 
                                lot.getId(), lot.getRemainingQty(), lot.isClosed(), lot.isClosed()))
                        .collect(java.util.stream.Collectors.joining(", ")));
                
                // Check if lots are being incorrectly filtered
                if (openLotsCount == 0 && inflatedLots.size() > 0) {
                    log.error("CRITICAL: Inflated {} lots but getOpenLots() returned 0. Checking if lots are incorrectly marked as closed:", inflatedLots.size());
                    for (TaxLot lot : inflatedLots) {
                        log.error("  Lot {}: remainingQty={}, isClosed()={}, compareTo(ZERO)={}", 
                                lot.getId(), lot.getRemainingQty(), 
                                lot.isClosed(),
                                lot.getRemainingQty() != null ? lot.getRemainingQty().compareTo(BigDecimal.ZERO) : "null");
                    }
                }
            } else {
                log.warn("Warning: No lots inflated from snapshot for position {}. Snapshot version: {}, compressed data length: {}", 
                        snapshot.getPositionKey(), snapshot.getLastVer(),
                        snapshot.getTaxLotsCompressed() != null ? snapshot.getTaxLotsCompressed().length() : 0);
            }
            
            return state;
        } catch (Exception e) {
            log.error("CRITICAL: Error inflating snapshot for position {}: {}. Exception type: {}. Compressed data: {}", 
                    snapshot.getPositionKey(), e.getMessage(), e.getClass().getName(),
                    snapshot.getTaxLotsCompressed() != null ? 
                        snapshot.getTaxLotsCompressed().substring(0, Math.min(200, snapshot.getTaxLotsCompressed().length())) : "null", e);
            log.error("Full stack trace:", e);
            
            // Check if this is the UnrecognizedPropertyException from old code
            if (e instanceof com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException) {
                log.error("CRITICAL: UnrecognizedPropertyException caught - this means old code is still running or readTree is failing!");
                log.error("This should not happen with manual JSON parsing. The exception suggests readValue is still being called somewhere.");
            }
            
            // Don't return empty state - throw exception so we can see what's wrong
            throw new RuntimeException("Failed to inflate snapshot for position " + snapshot.getPositionKey() + ": " + e.getMessage() + " (Exception type: " + e.getClass().getName() + ")", e);
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
        // CRITICAL FIX: Use getAllLots() to get the raw list (not filtered)
        // getAllLots() returns a new ArrayList copy of the raw openLots field
        // This ensures we save all lots including closed ones for history
        List<TaxLot> allLots = state.getAllLots();
        int openLotsCount = state.getOpenLots().size();
        BigDecimal totalQty = state.getTotalQty();
        
        log.info("Updating snapshot for position {}: state has {} total lots ({} open), total qty: {}", 
                snapshot.getPositionKey(), allLots.size(), openLotsCount, totalQty);
        
        // Log lot details before saving
        if (allLots.size() > 0) {
            log.info("Lots to save: {}", allLots.stream()
                    .map(lot -> String.format("Lot %s: qty=%s, closed=%s", lot.getId(), lot.getRemainingQty(), lot.isClosed()))
                    .collect(java.util.stream.Collectors.joining(", ")));
        }
        
        // Verify we have lots to save
        if (allLots.isEmpty() && openLotsCount > 0) {
            log.error("CRITICAL: getAllLots() returned empty but getOpenLots() has {} lots. This is a bug!", openLotsCount);
            // Fallback: try to get lots directly from the state's internal field
            // This shouldn't be necessary but let's be defensive
            throw new IllegalStateException("Cannot save snapshot: getAllLots() is empty but getOpenLots() has lots. Data inconsistency detected.");
        }
        
        if (allLots.isEmpty()) {
            log.warn("Warning: Saving snapshot with 0 lots for position {}", snapshot.getPositionKey());
        }
        
        CompressedLots compressed = CompressedLots.compress(allLots);
        String compressedJson = toJson(compressed);
        
        // Verify compression worked
        if (compressedJson == null || compressedJson.trim().isEmpty()) {
            log.error("CRITICAL: Compressed JSON is empty after compressing {} lots!", allLots.size());
            throw new IllegalStateException("Failed to compress lots: JSON is empty");
        }
        
        snapshot.setLastVer(version);
        snapshot.setTaxLotsCompressed(compressedJson);
        snapshot.setSummaryMetrics(toJson(createSummaryMetrics(state)));
        snapshot.setReconciliationStatus(reconciliationStatus);
        
        log.info("Updated snapshot for position {} to version {}, compressed {} lots, JSON length: {}, JSON preview: {}", 
                snapshot.getPositionKey(), version, allLots.size(), compressedJson.length(),
                compressedJson.length() > 0 ? compressedJson.substring(0, Math.min(150, compressedJson.length())) : "empty");
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
