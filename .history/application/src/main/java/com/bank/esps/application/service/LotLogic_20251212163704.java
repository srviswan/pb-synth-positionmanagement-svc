package com.bank.esps.application.service;

import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.domain.model.ContractRules;
import com.bank.esps.domain.model.LotAllocationResult;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.TaxLot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tax lot allocation logic (FIFO/LIFO/HIFO)
 * Handles adding and reducing lots based on contract rules
 */
@Component
public class LotLogic {
    
    private static final Logger log = LoggerFactory.getLogger(LotLogic.class);
    
    /**
     * Add a new lot to the position
     */
    public LotAllocationResult addLot(PositionState state, BigDecimal qty, BigDecimal price, LocalDate tradeDate) {
        String lotId = generateLotId();
        TaxLot newLot = TaxLot.builder()
                .id(lotId)
                .tradeDate(tradeDate)
                .currentRefPrice(price)
                .remainingQty(qty)
                .originalQty(qty) // Track original quantity for P&L calculation
                .originalPrice(price) // Track original price (cost basis) for P&L calculation
                .build();
        
        state.addLot(newLot);
        
        LotAllocationResult result = new LotAllocationResult();
        result.addAllocation(lotId, qty, price);
        
        BigDecimal notional = newLot.getRemainingNotional();
        log.info("✅ Added new lot: {} with qty: {}, price: {}, remaining notional: {}", lotId, qty, price, notional);
        return result;
    }
    
    /**
     * Reduce lots based on contract rules (FIFO/LIFO/HIFO)
     * @param closePrice The price at which the lots are being closed (for P&L calculation)
     */
    public LotAllocationResult reduceLots(PositionState state, BigDecimal qtyToReduce, ContractRules rules, BigDecimal closePrice) {
        if (qtyToReduce.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity to reduce must be positive");
        }
        
        BigDecimal remainingQty = qtyToReduce;
        LotAllocationResult result = new LotAllocationResult();
        // Get all lots from state (raw list, not filtered)
        List<TaxLot> allLots = state.getAllLots();
        // Filter to only open lots (remainingQty > 0)
        List<TaxLot> openLots = allLots.stream()
                .filter(lot -> lot.getRemainingQty() != null && lot.getRemainingQty().compareTo(BigDecimal.ZERO) > 0)
                .collect(java.util.stream.Collectors.toList());
        
        if (openLots.isEmpty()) {
            log.error("Cannot reduce quantity: no open lots available. Total lots in state: {}, open lots: {}", 
                    allLots.size(), openLots.size());
            if (!allLots.isEmpty()) {
                log.error("All lots are closed. Lot details: {}", 
                        allLots.stream()
                                .map(lot -> String.format("Lot %s: qty=%s, closed=%s", 
                                        lot.getId(), lot.getRemainingQty(), lot.isClosed()))
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
            throw new IllegalStateException("Cannot reduce quantity: no open lots available");
        }
        
        // Sort lots based on tax lot method
        List<TaxLot> sortedLots = sortLotsByMethod(openLots, rules.getTaxLotMethod());
        
        // Reduce lots in order
        for (TaxLot lot : sortedLots) {
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            
            BigDecimal lotQty = lot.getRemainingQty();
            BigDecimal reduceFromLot = remainingQty.min(lotQty);
            
            // Calculate P&L for this reduction
            BigDecimal realizedPnL = lot.calculateRealizedPnL(closePrice, reduceFromLot);
            
            // Record allocation with P&L
            result.addReduction(lot.getId(), reduceFromLot, closePrice, realizedPnL);
            
            // Calculate remaining notional before reduction
            BigDecimal notionalBefore = lot.getRemainingNotional();
            
            // Reduce lot quantity
            TaxLot reducedLot = lot.reduceQty(reduceFromLot);
            state.updateLot(reducedLot);
            
            // Calculate remaining notional after reduction
            BigDecimal notionalAfter = reducedLot.getRemainingNotional();
            
            // Remove if fully closed
            if (reducedLot.isClosed()) {
                state.removeLot(reducedLot.getId());
                log.info("✅ Closed lot: {} - Realized P&L: {}, closed qty: {}, close price: {}, original price: {}", 
                        lot.getId(), realizedPnL, reduceFromLot, closePrice, lot.getOriginalPrice());
            }
            
            remainingQty = remainingQty.subtract(reduceFromLot);
            
            log.info("Reduced lot: {} by qty: {}, remaining: {}, notional before: {}, notional after: {}, Realized P&L: {}", 
                    lot.getId(), reduceFromLot, reducedLot.getRemainingQty(), 
                    notionalBefore, notionalAfter, realizedPnL);
        }
        
        if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                    String.format("Cannot reduce quantity: insufficient lots. Requested: %s, Available: %s", 
                            qtyToReduce, qtyToReduce.subtract(remainingQty)));
        }
        
        return result;
    }
    
    /**
     * Sort lots based on tax lot method
     */
    private List<TaxLot> sortLotsByMethod(List<TaxLot> lots, TaxLotMethod method) {
        return switch (method) {
            case FIFO -> lots.stream()
                    .sorted(Comparator.comparing(TaxLot::getTradeDate)
                            .thenComparing(TaxLot::getId))
                    .collect(Collectors.toList());
            
            case LIFO -> lots.stream()
                    .sorted(Comparator.comparing(TaxLot::getTradeDate)
                            .reversed()
                            .thenComparing(TaxLot::getId))
                    .collect(Collectors.toList());
            
            case HIFO -> lots.stream()
                    .sorted(Comparator.comparing(TaxLot::getCurrentRefPrice)
                            .reversed()
                            .thenComparing(TaxLot::getTradeDate))
                    .collect(Collectors.toList());
        };
    }
    
    /**
     * Update all lot prices (for market data resets)
     */
    public void updateLotPrices(PositionState state, BigDecimal newPrice) {
        List<TaxLot> openLots = state.getOpenLots();
        for (TaxLot lot : openLots) {
            TaxLot updatedLot = lot.updatePrice(newPrice);
            state.updateLot(updatedLot);
        }
        log.debug("Updated prices for {} lots to {}", openLots.size(), newPrice);
    }
    
    /**
     * Generate unique lot ID
     */
    private String generateLotId() {
        return "LOT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
