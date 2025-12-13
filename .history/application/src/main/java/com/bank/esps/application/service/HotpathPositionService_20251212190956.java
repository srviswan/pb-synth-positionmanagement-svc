package com.bank.esps.application.service;

import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.*;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
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
    
    private final SnapshotRepository snapshotRepository;
    private final LotLogic lotLogic;
    private final ContractRulesService contractRulesService;
    private final IdempotencyService idempotencyService;
    private final RegulatorySubmissionService regulatorySubmissionService;
    private final UPIHistoryService upiHistoryService;
    private final SnapshotService snapshotService;
    private final EventStoreService eventStoreService;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Ensure EntityManager is injected (may be null in some test contexts)
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
    
    public HotpathPositionService(
            SnapshotRepository snapshotRepository,
            LotLogic lotLogic,
            ContractRulesService contractRulesService,
            IdempotencyService idempotencyService,
            RegulatorySubmissionService regulatorySubmissionService,
            UPIHistoryService upiHistoryService,
            SnapshotService snapshotService,
            EventStoreService eventStoreService) {
        this.snapshotRepository = snapshotRepository;
        this.lotLogic = lotLogic;
        this.contractRulesService = contractRulesService;
        this.idempotencyService = idempotencyService;
        this.regulatorySubmissionService = regulatorySubmissionService;
        this.upiHistoryService = upiHistoryService;
        this.snapshotService = snapshotService;
        this.eventStoreService = eventStoreService;
    }
    
    /**
     * Process current-dated or forward-dated trade
     * Synchronous processing with optimistic locking
     */
    @Transactional(rollbackFor = Exception.class)
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
            
            // Record UPI creation in history
            try {
                upiHistoryService.recordUPICreation(positionKey, snapshot.getUti(), tradeEvent);
            } catch (Exception e) {
                log.error("Error recording UPI creation for position {}", positionKey, e);
            }
        }
        
        long expectedVersion = snapshot.getLastVer() + 1;
        
        // 2. Apply business logic
        PositionState state = snapshotService.inflateSnapshot(snapshot);
        
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
                    com.fasterxml.jackson.databind.ObjectMapper debugMapper = new com.fasterxml.jackson.databind.ObjectMapper();
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
            result = lotLogic.reduceLots(state, tradeEvent.getQuantity(), rules, tradeEvent.getPrice());
            log.debug("After reducing lots, state now has {} open lots ({} total), total qty: {}", 
                    state.getOpenLots().size(), state.getAllLots().size(), state.getTotalQty());
        }
        
        // Verify state has lots before saving (especially for INCREASE after NEW_TRADE)
        int lotsBeforeSave = state.getAllLots().size();
        if (lotsBeforeSave == 0 && tradeEvent.isIncrease()) {
            log.error("ERROR: State has 0 lots after adding lot for trade {}. This should not happen!", tradeEvent.getTradeId());
        }
        
        // Check if position should be closed (qty = 0) or reopened (NEW_TRADE on TERMINATED position)
        // This check happens AFTER the trade is processed, so state reflects the new quantity
        BigDecimal totalQtyAfterTrade = state.getTotalQty();
        boolean positionClosed = totalQtyAfterTrade.compareTo(BigDecimal.ZERO) == 0;
        boolean isReopening = snapshot.getStatus() == com.bank.esps.domain.enums.PositionStatus.TERMINATED && 
                              tradeEvent.getTradeType().equals("NEW_TRADE");
        
        // Update position status BEFORE saving snapshot
        if (positionClosed && snapshot.getStatus() == com.bank.esps.domain.enums.PositionStatus.ACTIVE) {
            snapshot.setStatus(com.bank.esps.domain.enums.PositionStatus.TERMINATED);
            log.info("Position {} closed: quantity is zero ({}), status set to TERMINATED", positionKey, totalQtyAfterTrade);
            
            // Record UPI termination in history
            try {
                upiHistoryService.recordUPITermination(positionKey, snapshot.getUti(), tradeEvent);
            } catch (Exception e) {
                log.error("Error recording UPI termination for position {}", positionKey, e);
            }
        } else if (isReopening) {
            String previousUPI = snapshot.getUti();
            snapshot.setStatus(com.bank.esps.domain.enums.PositionStatus.ACTIVE);
            snapshot.setUti(tradeEvent.getTradeId()); // New UPI for reopened position
            log.info("Position {} reopened: NEW_TRADE on TERMINATED position, new UPI: {}, status set to ACTIVE", 
                    positionKey, tradeEvent.getTradeId());
            
            // Record UPI reopening in history
            try {
                upiHistoryService.recordUPIReopening(positionKey, tradeEvent.getTradeId(), previousUPI, tradeEvent);
            } catch (Exception e) {
                log.error("Error recording UPI reopening for position {}", positionKey, e);
            }
        }
        
        // 3. Persist event (source of truth)
        EventEntity event = createEventEntity(tradeEvent, expectedVersion, result);
        
        EventEntity savedEvent;
        try {
            savedEvent = eventStoreService.saveEvent(event);
            
            // Flush immediately to ensure event is persisted before snapshot update
            if (entityManager != null) {
                entityManager.flush();
            }
            
            log.info("✅ Saved event for position {}: version {}, type {}, effectiveDate {}, payload length: {}", 
                    positionKey, savedEvent.getEventVer(), savedEvent.getEventType(), 
                    savedEvent.getEffectiveDate(), 
                    savedEvent.getPayload() != null ? savedEvent.getPayload().length() : 0);
            
        } catch (DataIntegrityViolationException e) {
            // Concurrency detected: retry
            log.warn("Version conflict for position {}, retrying", positionKey, e);
            throw e;
        } catch (Exception e) {
            log.error("❌ CRITICAL: Error saving event for position {}: {}", positionKey, e.getMessage(), e);
            log.error("Event details: positionKey={}, eventVer={}, eventType={}, payload length={}", 
                    event.getPositionKey(), event.getEventVer(), event.getEventType(),
                    event.getPayload() != null ? event.getPayload().length() : 0);
            throw new RuntimeException("Failed to save event for position " + positionKey, e);
        }
        
        // Verify event was saved (within same transaction - should be visible)
        if (savedEvent.getEventVer() == null || savedEvent.getPositionKey() == null) {
            log.error("❌ CRITICAL: Saved event has null key fields! positionKey={}, eventVer={}", 
                    savedEvent.getPositionKey(), savedEvent.getEventVer());
            throw new IllegalStateException("Event was saved but has null key fields");
        }
        
        log.debug("✅ Event saved successfully: position {}, version {}", positionKey, expectedVersion);
        
        // 4. Update snapshot (cache) - ensure we save all lots
        // Wrap in try-catch to prevent transaction rollback if snapshot save fails
        try {
            snapshotService.updateSnapshot(snapshot, state, expectedVersion, ReconciliationStatus.RECONCILED, tradeEvent);
            
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
                // Don't throw - event is already saved, we don't want to rollback
                log.warn("Continuing despite empty snapshot - event is already persisted");
            } else {
                log.info("Successfully saved snapshot for position {}: version {}, compressed data length: {}, preview: {}", 
                        positionKey, savedSnapshot.getLastVer(), savedSnapshot.getTaxLotsCompressed().length(),
                        savedSnapshot.getTaxLotsCompressed().substring(0, Math.min(150, savedSnapshot.getTaxLotsCompressed().length())));
            }
            
            // Force flush to database to ensure data is committed
            if (entityManager != null) {
                entityManager.flush();
            }
            
            // 5. Submit to regulatory authorities (hotpath)
            // This happens after event and snapshot are successfully saved
            try {
                regulatorySubmissionService.submitTradeToRegulator(tradeEvent, savedSnapshot);
            } catch (Exception e) {
                // Log error but don't throw - regulatory submission failure shouldn't block trade processing
                log.error("Error submitting trade {} to regulator (trade already processed): {}", 
                        tradeEvent.getTradeId(), e.getMessage(), e);
            }
            
        } catch (Exception e) {
            // Log error but don't throw - event is already saved, we don't want to rollback the transaction
            log.error("❌ Error saving snapshot for position {} (event already saved): {}", positionKey, e.getMessage(), e);
            // Event is already saved, so we continue
        }
        
        // 5. Mark as processed
        try {
            idempotencyService.markAsProcessed(tradeEvent, expectedVersion);
        } catch (Exception e) {
            // Log error but don't throw - event is already saved
            log.error("❌ Error marking trade as processed for position {} (event already saved): {}", positionKey, e.getMessage(), e);
            // Event is already saved, so we continue
        }
        
        // Final verification: Query event store to confirm event was saved
        try {
            if (entityManager != null) {
                entityManager.flush(); // Ensure all changes are flushed
                // Clear persistence context to force fresh query
                entityManager.clear();
                
                // Query event store directly
                Optional<EventEntity> verifyEvent = eventStoreService.findEvent(positionKey, expectedVersion);
                
                if (verifyEvent.isPresent()) {
                    log.info("✅ FINAL VERIFICATION: Event confirmed in database - position {}, version {}, type {}", 
                            positionKey, expectedVersion, verifyEvent.get().getEventType());
                } else {
                    log.error("❌ FINAL VERIFICATION FAILED: Event NOT found in database after save! Position: {}, Version: {}", 
                            positionKey, expectedVersion);
                    log.error("This indicates the transaction may have rolled back or the event was not actually saved.");
                }
            }
        } catch (Exception e) {
            log.error("Error during final verification: {}", e.getMessage(), e);
        }
        
        log.info("✅ Successfully processed trade {} for position {}, version {} - Transaction should commit now", 
                tradeEvent.getTradeId(), positionKey, expectedVersion);
    }
    
    /**
     * Inflate snapshot to PositionState
     * Made public for use in controllers/diagnostics
     * Delegates to SnapshotService for backward compatibility
     */
    public PositionState inflateSnapshot(SnapshotEntity snapshot) {
        return snapshotService.inflateSnapshot(snapshot);
    }
    
}
