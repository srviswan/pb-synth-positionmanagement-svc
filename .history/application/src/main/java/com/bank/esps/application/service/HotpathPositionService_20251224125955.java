package com.bank.esps.application.service;

import com.bank.esps.application.statemachine.PositionStateMachine;
import com.bank.esps.application.util.PositionKeyGenerator;
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
    private final PositionStateMachine stateMachine;
    private final PositionKeyGenerator positionKeyGenerator;
    
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
            EventStoreService eventStoreService,
            PositionStateMachine stateMachine,
            PositionKeyGenerator positionKeyGenerator) {
        this.snapshotRepository = snapshotRepository;
        this.lotLogic = lotLogic;
        this.contractRulesService = contractRulesService;
        this.idempotencyService = idempotencyService;
        this.regulatorySubmissionService = regulatorySubmissionService;
        this.upiHistoryService = upiHistoryService;
        this.snapshotService = snapshotService;
        this.eventStoreService = eventStoreService;
        this.stateMachine = stateMachine;
        this.positionKeyGenerator = positionKeyGenerator;
    }
    
    /**
     * Process current-dated or forward-dated trade
     * Synchronous processing with optimistic locking
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Retryable(
            retryFor = {
                DataIntegrityViolationException.class,
                org.hibernate.StaleObjectStateException.class,
                org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                org.hibernate.dialect.lock.OptimisticEntityLockException.class
            },
            maxAttempts = 5,  // Increased for optimistic locking conflicts
            backoff = @Backoff(delay = 25, multiplier = 1.5, maxDelay = 200),  // Faster retries with cap
            noRetryFor = {IllegalArgumentException.class, IllegalStateException.class} // Don't retry on validation errors
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
            snapshot = snapshotService.createNewSnapshot(positionKey, tradeEvent);
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
        
        // Capture quantity BEFORE trade to detect sign changes (long to short or short to long)
        BigDecimal quantityBeforeTrade = totalQty;
        boolean wasLong = quantityBeforeTrade.compareTo(BigDecimal.ZERO) > 0;
        boolean wasShort = quantityBeforeTrade.compareTo(BigDecimal.ZERO) < 0;
        boolean wasFlat = quantityBeforeTrade.compareTo(BigDecimal.ZERO) == 0;
        
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
        com.bank.esps.domain.model.Contract contract = contractRulesService.getContract(tradeEvent.getContractId());
        LotAllocationResult result;
        
        if (tradeEvent.isIncrease()) {
            // For INCREASE: Add lot normally
            // If we're on a short position, the lot will have negative quantity
            // If we're on a long position, the lot will have positive quantity
            // Sign change detection below will handle long->short or short->long transitions
            result = lotLogic.addLot(state, tradeEvent.getQuantity(), 
                    tradeEvent.getPrice(), tradeEvent.getEffectiveDate());
            log.debug("After adding lot, state now has {} open lots ({} total), total qty: {}", 
                    state.getOpenLots().size(), state.getAllLots().size(), state.getTotalQty());
        } else {
            result = lotLogic.reduceLots(state, tradeEvent.getQuantity(), contract, tradeEvent.getPrice());
            log.debug("After reducing lots, state now has {} open lots ({} total), total qty: {}", 
                    state.getOpenLots().size(), state.getAllLots().size(), state.getTotalQty());
            
            // Handle sign change: If remaining quantity is negative, we need to create a short position
            // This happens when a DECREASE on a long position exceeds the available long quantity
            if (result.getRemainingQuantity() != null && result.getRemainingQuantity().compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal shortQty = result.getRemainingQuantity(); // Negative quantity for short position
                log.info("⚠️ Sign change detected during lot reduction: Creating short lot with quantity {} at price {}", 
                        shortQty, tradeEvent.getPrice());
                
                // Create a short lot with negative quantity
                // PositionState.getTotalQty() will sum all lots (including negative ones) correctly
                lotLogic.addLot(state, shortQty, tradeEvent.getPrice(), tradeEvent.getEffectiveDate());
                
                log.info("Created short lot: quantity {}, price {}, new total qty: {}", 
                        shortQty, tradeEvent.getPrice(), state.getTotalQty());
            }
        }
        
        // Note: Sign change detection happens below after lot processing
        // The sign change logic will handle closing old position and creating new one
        
        // Verify state has lots before saving (especially for INCREASE after NEW_TRADE)
        int lotsBeforeSave = state.getAllLots().size();
        if (lotsBeforeSave == 0 && tradeEvent.isIncrease()) {
            log.error("ERROR: State has 0 lots after adding lot for trade {}. This should not happen!", tradeEvent.getTradeId());
        }
        
        // Use state machine to determine new state after trade processing
        // This check happens AFTER the trade is processed, so state reflects the new quantity
        BigDecimal totalQtyAfterTrade = state.getTotalQty();
        
        // Detect sign change: Long to Short or Short to Long
        boolean isLongAfter = totalQtyAfterTrade.compareTo(BigDecimal.ZERO) > 0;
        boolean isShortAfter = totalQtyAfterTrade.compareTo(BigDecimal.ZERO) < 0;
        boolean isFlatAfter = totalQtyAfterTrade.compareTo(BigDecimal.ZERO) == 0;
        
        boolean signChanged = (wasLong && isShortAfter) || (wasShort && isLongAfter);
        
        if (signChanged) {
            log.info("⚠️ SIGN CHANGE DETECTED for position {}: {} -> {} (quantity: {} -> {})", 
                    positionKey, 
                    wasLong ? "LONG" : "SHORT",
                    isLongAfter ? "LONG" : "SHORT",
                    quantityBeforeTrade, totalQtyAfterTrade);
            
            // Generate new position_key for opposite direction
            // Need account, instrument, currency from TradeEvent
            if (tradeEvent.getAccount() == null || tradeEvent.getInstrument() == null || tradeEvent.getCurrency() == null) {
                log.error("❌ Cannot create new position_key for sign change: missing account/instrument/currency in TradeEvent");
                throw new IllegalStateException(
                    String.format("Cannot transition position from %s to %s: TradeEvent missing account/instrument/currency. " +
                            "Required for generating new position_key.", 
                            wasLong ? "LONG" : "SHORT", isLongAfter ? "LONG" : "SHORT"));
            }
            
            // Generate new position_key with opposite direction
            String newPositionKey = positionKeyGenerator.generatePositionKey(
                    tradeEvent.getAccount(),
                    tradeEvent.getInstrument(),
                    tradeEvent.getCurrency(),
                    isShortAfter); // true for SHORT, false for LONG
            
            log.info("🔄 Generating new position_key for sign change: {} -> {} (old: {}, new: {})", 
                    wasLong ? "LONG" : "SHORT",
                    isLongAfter ? "LONG" : "SHORT",
                    positionKey, newPositionKey);
            
            // 1. Save event for old position (the DECREASE/INCREASE that caused sign change)
            EventEntity oldPositionEvent = eventStoreService.createEventEntity(tradeEvent, expectedVersion, result);
            try {
                eventStoreService.saveEvent(oldPositionEvent);
                log.info("✅ Saved event for old position {} (sign change trigger)", positionKey);
            } catch (Exception e) {
                log.error("Error saving event for old position {}", positionKey, e);
                throw new RuntimeException("Failed to save event for old position", e);
            }
            
            // 2. Close old position (TERMINATED)
            String oldUPI = snapshot.getUti();
            snapshot.setStatus(com.bank.esps.domain.enums.PositionStatus.TERMINATED);
            
            // Update snapshot to reflect closure (quantity is zero after closing all lots)
            snapshotService.updateSnapshot(snapshot, state, expectedVersion, 
                    com.bank.esps.domain.enums.ReconciliationStatus.RECONCILED, tradeEvent);
            
            // Save old position closure
            try {
                snapshotRepository.saveAndFlush(snapshot);
                log.info("✅ Closed old position {} (TERMINATED)", positionKey);
            } catch (Exception e) {
                log.error("Error closing old position {}", positionKey, e);
                throw new RuntimeException("Failed to close old position", e);
            }
            
            // Record UPI termination for old position
            try {
                upiHistoryService.recordUPIChange(
                    positionKey, oldUPI, oldUPI,
                    com.bank.esps.domain.enums.PositionStatus.TERMINATED,
                    com.bank.esps.domain.enums.PositionStatus.ACTIVE,
                    "TERMINATED",
                    tradeEvent,
                    String.format("Position closed due to sign change: %s -> %s (trade %s)", 
                            wasLong ? "LONG" : "SHORT",
                            isLongAfter ? "LONG" : "SHORT",
                            tradeEvent.getTradeId()));
            } catch (Exception e) {
                log.error("Error recording UPI termination for sign change on position {}", positionKey, e);
            }
            
            // 3. Create new position with new position_key and new UPI
            String newUPI = tradeEvent.getTradeId(); // Use trade ID as new UPI
            
            // Create new snapshot for new position_key
            SnapshotEntity newSnapshot = snapshotService.createNewSnapshot(newPositionKey, tradeEvent);
            newSnapshot.setUti(newUPI);
            newSnapshot.setStatus(com.bank.esps.domain.enums.PositionStatus.ACTIVE);
            
            // Create state for new position (remaining quantity after sign change)
            PositionState newState = new com.bank.esps.domain.model.PositionState();
            // Add lot with the remaining quantity (negative for short, positive for long)
            lotLogic.addLot(newState, totalQtyAfterTrade, tradeEvent.getPrice(), tradeEvent.getEffectiveDate());
            
            // Update snapshot with new state
            snapshotService.updateSnapshot(newSnapshot, newState, 1L, 
                    com.bank.esps.domain.enums.ReconciliationStatus.RECONCILED, tradeEvent);
            
            // Save new position
            try {
                newSnapshot = snapshotRepository.saveAndFlush(newSnapshot);
                log.info("✅ Created new position {} (ACTIVE, {}) with UPI {}", 
                        newPositionKey, isLongAfter ? "LONG" : "SHORT", newUPI);
            } catch (Exception e) {
                log.error("Error creating new position {}", newPositionKey, e);
                throw new RuntimeException("Failed to create new position", e);
            }
            
            // Record UPI creation for new position
            try {
                upiHistoryService.recordUPICreation(newPositionKey, newUPI, tradeEvent);
            } catch (Exception e) {
                log.error("Error recording UPI creation for sign change on position {}", newPositionKey, e);
            }
            
            // 4. Create event for new position (NEW_TRADE on new position_key)
            TradeEvent newPositionTrade = TradeEvent.builder()
                    .tradeId(tradeEvent.getTradeId() + "-NEW-POSITION")
                    .positionKey(newPositionKey)
                    .account(tradeEvent.getAccount())
                    .instrument(tradeEvent.getInstrument())
                    .currency(tradeEvent.getCurrency())
                    .tradeType("NEW_TRADE")
                    .quantity(totalQtyAfterTrade.abs()) // Use absolute value
                    .price(tradeEvent.getPrice())
                    .effectiveDate(tradeEvent.getEffectiveDate())
                    .contractId(tradeEvent.getContractId())
                    .correlationId(tradeEvent.getCorrelationId())
                    .causationId(tradeEvent.getTradeId())
                    .userId(tradeEvent.getUserId())
                    .build();
            
            // Save event for new position
            EventEntity newPositionEvent = eventStoreService.createEventFromTrade(newPositionTrade, 1L);
            eventStoreService.saveEvent(newPositionEvent);
            
            // Mark trade as processed for new position (idempotency)
            try {
                idempotencyService.markAsProcessed(newPositionTrade, 1L);
            } catch (Exception e) {
                log.warn("Error marking new position trade as processed", e);
            }
            
            log.info("✅ Position transitioned: Old position_key {} (TERMINATED, UPI {}) -> New position_key {} (ACTIVE, UPI {}, {})", 
                    positionKey, oldUPI, newPositionKey, newUPI, isLongAfter ? "LONG" : "SHORT");
            
            // Return early - don't process further on old position_key
            return;
        } else {
            // Normal state machine transition (no sign change)
            // Get current state
            PositionStateMachine.State currentState = stateMachine.getCurrentState(
                    snapshotOpt.isPresent(), 
                    snapshotOpt.isPresent() ? snapshot.getStatus() : null);
            
            // Get event from trade type
            PositionStateMachine.Event stateMachineEvent = stateMachine.fromTradeType(tradeEvent.getTradeType());
            
            // Apply state transition
            PositionStateMachine.TransitionResult transitionResult = 
                    stateMachine.transition(currentState, stateMachineEvent, totalQtyAfterTrade);
            
            if (!transitionResult.isValid()) {
                log.error("Invalid state transition for position {}: {} + {} -> {}", 
                        positionKey, currentState, stateMachineEvent, transitionResult.getErrorMessage());
                throw new IllegalStateException("Invalid state transition: " + transitionResult.getErrorMessage());
            }
            
            // Update position status based on state machine result
            PositionStateMachine.State newState = transitionResult.getNewState();
            com.bank.esps.domain.enums.PositionStatus newStatus = stateMachine.toPositionStatus(newState);
            
            if (transitionResult.isStateChanged()) {
                com.bank.esps.domain.enums.PositionStatus oldStatus = snapshot.getStatus();
                snapshot.setStatus(newStatus);
                
                // Handle state change side effects
                if (oldStatus == com.bank.esps.domain.enums.PositionStatus.ACTIVE && 
                    newStatus == com.bank.esps.domain.enums.PositionStatus.TERMINATED) {
                    // Position closed: quantity is zero
                    log.info("Position {} closed: quantity is zero ({}), status set to TERMINATED", 
                            positionKey, totalQtyAfterTrade);
                    
                    // Record UPI termination in history
                    try {
                        upiHistoryService.recordUPITermination(positionKey, snapshot.getUti(), tradeEvent);
                    } catch (Exception e) {
                        log.error("Error recording UPI termination for position {}", positionKey, e);
                    }
                } else if (oldStatus == com.bank.esps.domain.enums.PositionStatus.TERMINATED && 
                           newStatus == com.bank.esps.domain.enums.PositionStatus.ACTIVE) {
                    // Position reopened: NEW_TRADE on TERMINATED position
                    String previousUPI = snapshot.getUti();
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
            }
        }
        
        // 3. Persist event (source of truth)
        EventEntity eventEntity = eventStoreService.createEventEntity(tradeEvent, expectedVersion, result);
        
        EventEntity savedEvent;
        try {
            savedEvent = eventStoreService.saveEvent(eventEntity);
            
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
                    eventEntity.getPositionKey(), eventEntity.getEventVer(), eventEntity.getEventType(),
                    eventEntity.getPayload() != null ? eventEntity.getPayload().length() : 0);
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
