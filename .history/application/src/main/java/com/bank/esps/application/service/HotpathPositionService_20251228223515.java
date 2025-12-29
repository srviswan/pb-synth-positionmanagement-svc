package com.bank.esps.application.service;

import com.bank.esps.domain.cache.CacheService;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.enums.TradeSequenceStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.messaging.MessageProducer;
import com.bank.esps.domain.model.LotAllocationResult;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Hotpath service for processing current/forward-dated trades synchronously
 * Target latency: <100ms p99
 */
@Service
public class HotpathPositionService {
    
    private static final Logger log = LoggerFactory.getLogger(HotpathPositionService.class);
    
    private final EventStoreService eventStoreService;
    private final CacheService cacheService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final LotLogic lotLogic;
    private final TradeClassifier tradeClassifier;
    private final IdempotencyService idempotencyService;
    private final MessageProducer messageProducer;
    private final TradeValidationService validationService;
    private final CorrelationIdService correlationIdService;
    private final MetricsService metricsService;
    private final PositionKeyGenerator positionKeyGenerator;
    private final ContractRulesService contractRulesService;
    
    @Value("${app.kafka.topics.backdated-trades:backdated-trades}")
    private String backdatedTradesTopic;
    
    @Value("${app.kafka.topics.trade-applied-events:trade-applied-events}")
    private String tradeAppliedEventsTopic;
    
    @Value("${app.kafka.topics.provisional-trade-events:provisional-trade-events}")
    private String provisionalTradeEventsTopic;
    
    public HotpathPositionService(
            EventStoreService eventStoreService,
            CacheService cacheService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            LotLogic lotLogic,
            TradeClassifier tradeClassifier,
            IdempotencyService idempotencyService,
            MessageProducer messageProducer,
            TradeValidationService validationService,
            CorrelationIdService correlationIdService,
            MetricsService metricsService,
            PositionKeyGenerator positionKeyGenerator,
            ContractRulesService contractRulesService) {
        this.eventStoreService = eventStoreService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.lotLogic = lotLogic;
        this.tradeClassifier = tradeClassifier;
        this.idempotencyService = idempotencyService;
        this.messageProducer = messageProducer;
        this.validationService = validationService;
        this.correlationIdService = correlationIdService;
        this.metricsService = metricsService;
        this.positionKeyGenerator = positionKeyGenerator;
        this.contractRulesService = contractRulesService;
    }
    
    /**
     * Process trade in hotpath (current/forward-dated) or route to coldpath (backdated)
     */
    @Transactional
    public PositionState processTrade(TradeEvent tradeEvent) {
        // Generate correlation ID if not present
        String correlationId = correlationIdService.getCurrentCorrelationId();
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = correlationIdService.generateCorrelationId();
        }
        
        // Note: Validation is now done in TradeController before calling this method
        // This ensures validation happens early and failed trades go to DLQ
        
        // Check idempotency
        if (idempotencyService.isAlreadyProcessed(tradeEvent.getTradeId())) {
            log.info("Trade {} already processed, returning cached state", tradeEvent.getTradeId());
            String positionKey = tradeEvent.getPositionKey();
            if (positionKey == null || positionKey.isEmpty()) {
                positionKey = positionKeyGenerator.generatePositionKeyFromTrade(tradeEvent);
            }
            return getCurrentState(positionKey);
        }
        
        // Generate or use provided position key with direction
        String positionKey = tradeEvent.getPositionKey();
        if (positionKey == null || positionKey.isEmpty()) {
            // Generate position key with direction based on quantity sign
            positionKey = positionKeyGenerator.generatePositionKeyFromTrade(tradeEvent);
            tradeEvent.setPositionKey(positionKey);
        }
        
        // Load current state
        PositionState currentState = getCurrentState(positionKey);
        
        // Classify trade
        TradeSequenceStatus sequenceStatus = tradeClassifier.classifyTrade(tradeEvent, currentState);
        
        // Check for sign change (direction change) - only for current/forward-dated trades
        if (sequenceStatus != TradeSequenceStatus.BACKDATED && 
            currentState != null && currentState.getOpenLots() != null && !currentState.getOpenLots().isEmpty()) {
            PositionKeyGenerator.Direction currentDirection = positionKeyGenerator.extractDirection(currentState);
            
            // Calculate what the position would be after this trade
            java.math.BigDecimal currentTotalQty = currentState.getTotalQty();
            java.math.BigDecimal tradeQty = tradeEvent.getQuantity() != null ? tradeEvent.getQuantity() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal newTotalQty = currentTotalQty.add(tradeQty);
            
            // Detect sign change: direction would flip
            boolean wouldChangeDirection = (currentDirection == PositionKeyGenerator.Direction.LONG && 
                                          newTotalQty.compareTo(java.math.BigDecimal.ZERO) < 0) ||
                                         (currentDirection == PositionKeyGenerator.Direction.SHORT && 
                                          newTotalQty.compareTo(java.math.BigDecimal.ZERO) > 0);
            
            if (wouldChangeDirection) {
                log.info("Sign change detected: {} position would become {} (qty: {} -> {})", 
                        currentDirection, 
                        currentDirection == PositionKeyGenerator.Direction.LONG ? "SHORT" : "LONG",
                        currentTotalQty, newTotalQty);
                
                // Handle sign change: create new position key for opposite direction
                return handleSignChange(tradeEvent, currentState, currentDirection, newTotalQty);
            }
        }
        
        // Record metrics
        io.micrometer.core.instrument.Timer.Sample processingSample = metricsService.startHotpathProcessing();
        try {
            // Route based on classification
            if (sequenceStatus == TradeSequenceStatus.BACKDATED) {
                metricsService.recordColdpathTrade();
                return handleBackdatedTrade(tradeEvent, currentState);
            } else {
                metricsService.recordHotpathTrade();
                return handleCurrentOrForwardDatedTrade(tradeEvent, currentState, sequenceStatus);
            }
        } finally {
            metricsService.recordHotpathProcessing(processingSample);
            metricsService.recordTradeProcessed();
        }
    }
    
    /**
     * Handle current-dated or forward-dated trades synchronously
     */
    private PositionState handleCurrentOrForwardDatedTrade(
            TradeEvent tradeEvent, PositionState currentState, TradeSequenceStatus sequenceStatus) {
        
        log.info("Processing {} trade {} in hotpath", sequenceStatus, tradeEvent.getTradeId());
        
        // Apply trade
        PositionState newState = applyTrade(currentState, tradeEvent);
        
        // Update PriceQuantity Schedule
        updatePriceQuantitySchedule(newState, tradeEvent);
        
        // Save event with correlation ID
        int nextVersion = eventStoreService.getNextVersion(tradeEvent.getPositionKey());
        String correlationId = correlationIdService.getCurrentCorrelationId();
        String causationId = tradeEvent.getTradeId(); // Use trade ID as causation ID
        eventStoreService.appendEvent(tradeEvent.getPositionKey(), tradeEvent, nextVersion, 
                correlationId, causationId);
        newState.setVersion(nextVersion);
        
        // Record idempotency
        idempotencyService.recordProcessed(tradeEvent.getTradeId(), 
                tradeEvent.getPositionKey(), nextVersion);
        
        // Save snapshot with RECONCILED status
        eventStoreService.saveSnapshot(tradeEvent.getPositionKey(), newState, 
                ReconciliationStatus.RECONCILED, null);
        
        // Update cache
        cacheService.put("position:" + tradeEvent.getPositionKey(), newState);
        
        // Publish trade applied event (async, non-blocking)
        try {
            String eventJson = objectMapper.writeValueAsString(newState);
            messageProducer.send(tradeAppliedEventsTopic, tradeEvent.getPositionKey(), eventJson);
        } catch (Exception e) {
            log.warn("Failed to publish trade applied event for trade {}", tradeEvent.getTradeId(), e);
            // Don't fail the transaction
        }
        
        log.info("Processed {} trade {} for position: {}, version: {}", 
                sequenceStatus, tradeEvent.getTradeId(), tradeEvent.getPositionKey(), nextVersion);
        return newState;
    }
    
    /**
     * Handle backdated trades: route to coldpath and create provisional position
     */
    private PositionState handleBackdatedTrade(TradeEvent tradeEvent, PositionState currentState) {
        log.info("Routing backdated trade {} to coldpath", tradeEvent.getTradeId());
        
        // Route to coldpath topic (async, non-blocking)
        try {
            String tradeJson = objectMapper.writeValueAsString(tradeEvent);
            messageProducer.send(backdatedTradesTopic, tradeEvent.getPositionKey(), tradeJson);
            log.debug("Published backdated trade {} to coldpath topic", tradeEvent.getTradeId());
        } catch (Exception e) {
            log.error("Failed to route backdated trade {} to coldpath", tradeEvent.getTradeId(), e);
            // Continue with provisional position creation even if routing fails
        }
        
        // Calculate provisional position (dirty estimate)
        PositionState provisionalState = applyTrade(currentState, tradeEvent);
        
        // Save provisional snapshot
        int nextVersion = eventStoreService.getNextVersion(tradeEvent.getPositionKey());
        eventStoreService.saveSnapshot(tradeEvent.getPositionKey(), provisionalState, 
                ReconciliationStatus.PROVISIONAL, tradeEvent.getTradeId());
        provisionalState.setVersion(nextVersion);
        
        // Update cache with provisional state
        cacheService.put("position:" + tradeEvent.getPositionKey(), provisionalState);
        
        // Publish provisional trade event (async, non-blocking)
        try {
            String eventJson = objectMapper.writeValueAsString(provisionalState);
            messageProducer.send(provisionalTradeEventsTopic, tradeEvent.getPositionKey(), eventJson);
        } catch (Exception e) {
            log.warn("Failed to publish provisional trade event for trade {}", tradeEvent.getTradeId(), e);
        }
        
        // Record metrics
        metricsService.recordProvisionalPosition();
        
        log.info("Created provisional position for backdated trade {}: position={}, version={}", 
                tradeEvent.getTradeId(), tradeEvent.getPositionKey(), nextVersion);
        
        return provisionalState;
    }
    
    /**
     * Apply trade to position state
     * Note: With direction-based position keys, each position has a single direction
     * - LONG positions: always positive quantities
     * - SHORT positions: always negative quantities (separate position_key)
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
        
        // Determine position direction from position key
        PositionKeyGenerator.Direction direction = positionKeyGenerator.extractDirection(currentState);
        
        if (direction == PositionKeyGenerator.Direction.LONG) {
            // LONG position: quantities should be positive
            if (quantity.compareTo(java.math.BigDecimal.ZERO) > 0) {
                // Increase: Add new tax lot
                lotLogic.addLot(currentState, quantity, price, 
                        tradeEvent.getTradeDate(), tradeEvent.getSettlementDate());
            } else if (quantity.compareTo(java.math.BigDecimal.ZERO) < 0) {
                // Decrease: Reduce existing lots using contract rules (FIFO/LIFO/HIFO)
                java.math.BigDecimal qtyToReduce = quantity.abs();
                // Get tax lot method from contract rules (default to FIFO if no contract ID)
                String contractId = tradeEvent.getContractId() != null ? tradeEvent.getContractId() : null;
                LotLogic.TaxLotMethod method = contractRulesService.getTaxLotMethod(contractId);
                LotAllocationResult result = lotLogic.reduceLots(
                        currentState, qtyToReduce, method, price);
                
                if (!result.isFullyAllocated()) {
                    log.warn("Could not fully reduce LONG position. Trade: {}, Requested: {}, Remaining: {}", 
                            tradeEvent.getTradeId(), qtyToReduce, result.getRemainingQtyToAllocate());
                }
            }
        } else {
            // SHORT position: quantities should be negative
            // For SHORT: positive quantity = increasing short (selling more), negative = decreasing short (buying back)
            if (quantity.compareTo(java.math.BigDecimal.ZERO) < 0) {
                // Increasing short position: Add new tax lot with negative quantity
                lotLogic.addLot(currentState, quantity, price, 
                        tradeEvent.getTradeDate(), tradeEvent.getSettlementDate());
            } else if (quantity.compareTo(java.math.BigDecimal.ZERO) > 0) {
                // Decreasing short position: Reduce existing lots (buying back)
                // Get tax lot method from contract rules (default to FIFO if no contract ID)
                String contractId = tradeEvent.getContractId() != null ? tradeEvent.getContractId() : null;
                LotLogic.TaxLotMethod method = contractRulesService.getTaxLotMethod(contractId);
                LotAllocationResult result = lotLogic.reduceLots(
                        currentState, quantity, method, price);
                
                if (!result.isFullyAllocated()) {
                    log.warn("Could not fully reduce SHORT position. Trade: {}, Requested: {}, Remaining: {}", 
                            tradeEvent.getTradeId(), quantity, result.getRemainingQtyToAllocate());
                }
            }
        }
        
        return currentState;
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
    
    /**
     * Handle sign change: create new position key for opposite direction
     * This closes the current position and creates a new position with opposite direction
     */
    private PositionState handleSignChange(TradeEvent tradeEvent, PositionState currentState, 
                                          PositionKeyGenerator.Direction currentDirection, 
                                          java.math.BigDecimal newTotalQty) {
        log.info("Handling sign change: closing {} position and creating {} position (qty: {} -> {})", 
                currentDirection, 
                currentDirection == PositionKeyGenerator.Direction.LONG ? "SHORT" : "LONG",
                currentState.getTotalQty(), newTotalQty);
        
        // 1. Close current position by reducing all lots to zero
        java.math.BigDecimal currentTotalQty = currentState.getTotalQty();
        java.math.BigDecimal qtyToClose = currentTotalQty.abs();
        
        // Reduce all lots to zero (close position)
        if (qtyToClose.compareTo(java.math.BigDecimal.ZERO) > 0) {
            LotAllocationResult closeResult = lotLogic.reduceLots(
                    currentState, qtyToClose, LotLogic.TaxLotMethod.FIFO, tradeEvent.getPrice());
            
            // All lots should be closed
            currentState.getOpenLots().clear();
        }
        
        // Save closed position snapshot
        int closeVersion = eventStoreService.getNextVersion(currentState.getPositionKey());
        currentState.setVersion(closeVersion);
        eventStoreService.saveSnapshot(currentState.getPositionKey(), currentState, 
                com.bank.esps.domain.enums.ReconciliationStatus.RECONCILED, null);
        
        // Record idempotency for the close operation
        idempotencyService.recordProcessed(tradeEvent.getTradeId() + "_CLOSE", 
                currentState.getPositionKey(), closeVersion);
        
        // 2. Generate new position key for opposite direction
        PositionKeyGenerator.Direction newDirection = (currentDirection == PositionKeyGenerator.Direction.LONG) 
                ? PositionKeyGenerator.Direction.SHORT 
                : PositionKeyGenerator.Direction.LONG;
        
        String newPositionKey = positionKeyGenerator.generatePositionKey(
                tradeEvent.getAccount(),
                tradeEvent.getInstrument(),
                tradeEvent.getCurrency(),
                newDirection);
        
        log.info("Created new position key {} for {} direction (was {})", 
                newPositionKey, newDirection, currentDirection);
        
        // 3. Create new position with remaining quantity
        PositionState newState = new PositionState(
                newPositionKey,
                tradeEvent.getAccount(),
                tradeEvent.getInstrument(),
                tradeEvent.getCurrency());
        
        // Apply the remaining quantity as a new trade on the new position
        TradeEvent newTradeEvent = TradeEvent.builder()
                .tradeId(tradeEvent.getTradeId() + "_TRANSITION")
                .account(tradeEvent.getAccount())
                .instrument(tradeEvent.getInstrument())
                .currency(tradeEvent.getCurrency())
                .quantity(newTotalQty) // This will be negative for SHORT, positive for LONG
                .price(tradeEvent.getPrice())
                .tradeDate(tradeEvent.getTradeDate())
                .effectiveDate(tradeEvent.getEffectiveDate())
                .settlementDate(tradeEvent.getSettlementDate())
                .positionKey(newPositionKey)
                .build();
        
        // Apply trade to new position
        newState = applyTrade(newState, newTradeEvent);
        
        // 4. Save new position
        int newVersion = eventStoreService.getNextVersion(newPositionKey);
        newState.setVersion(newVersion);
        eventStoreService.appendEvent(newPositionKey, newTradeEvent, newVersion,
                correlationIdService.getCurrentCorrelationId(),
                tradeEvent.getTradeId());
        eventStoreService.saveSnapshot(newPositionKey, newState, 
                com.bank.esps.domain.enums.ReconciliationStatus.RECONCILED, null);
        
        // Update cache
        cacheService.put("position:" + newPositionKey, newState);
        
        // Record idempotency
        idempotencyService.recordProcessed(tradeEvent.getTradeId(), newPositionKey, newVersion);
        
        // Publish trade applied event
        try {
            String eventJson = objectMapper.writeValueAsString(newState);
            messageProducer.send(tradeAppliedEventsTopic, newPositionKey, eventJson);
        } catch (Exception e) {
            log.warn("Failed to publish trade applied event for trade {}", tradeEvent.getTradeId(), e);
        }
        
        log.info("Sign change completed: closed {} position {}, created {} position {} with qty {}", 
                currentDirection, currentState.getPositionKey(),
                newDirection, newPositionKey, newTotalQty);
        
        return newState;
    }
    
    /**
     * Update PriceQuantity Schedule for position (Hybrid Approach)
     * 
     * Follows the hybrid approach from settlement_date_quantity_tracking_solution.md:
     * - Trade Date: When trade was executed (tradeEvent.getTradeDate())
     * - Settlement Date: When quantity was actually settled (tradeEvent.getSettlementDate())
     * - Effective Date: Settlement date (used for interest accrual calculations)
     * - Settled Quantity: Quantity that was actually settled (from tax lots)
     */
    private void updatePriceQuantitySchedule(PositionState state, TradeEvent tradeEvent) {
        if (state.getPriceQuantitySchedule() == null) {
            state.setPriceQuantitySchedule(new com.bank.esps.domain.model.PriceQuantitySchedule());
            state.getPriceQuantitySchedule().setUnit("SHARES");
            state.getPriceQuantitySchedule().setCurrency(tradeEvent.getCurrency() != null ? tradeEvent.getCurrency() : "USD");
        }
        
        java.time.LocalDate tradeDate = tradeEvent.getTradeDate();
        java.time.LocalDate settlementDate = tradeEvent.getSettlementDate() != null 
                ? tradeEvent.getSettlementDate() 
                : tradeEvent.getEffectiveDate(); // Fallback to effectiveDate if settlementDate not provided
        
        // If still null, use tradeDate as settlementDate (T+0 settlement)
        if (settlementDate == null) {
            settlementDate = tradeDate;
        }
        
        if (tradeDate != null) {
            java.math.BigDecimal currentQty = state.getTotalQty();
            java.math.BigDecimal weightedAvgPrice = state.getWeightedAveragePrice();
            
            // Calculate settled quantity from open lots
            // For interest accrual, we need the settled quantity (not just effective quantity)
            java.math.BigDecimal settledQuantity = calculateSettledQuantity(state);
            
            // Add entry with hybrid approach: tradeDate, settlementDate, quantity, settledQuantity, price
            state.getPriceQuantitySchedule().addOrUpdateEntry(
                    tradeDate,           // Trade date (when trade was executed)
                    settlementDate,      // Settlement date (when interest accrual starts)
                    currentQty,          // Effective quantity at trade date
                    settledQuantity,     // Settled quantity (for interest accrual)
                    weightedAvgPrice    // Weighted average price
            );
        }
    }
    
    /**
     * Calculate settled quantity from open lots
     * This is the sum of settledQuantity from all open lots
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
}
