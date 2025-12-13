package com.bank.esps.application.service;

import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.domain.model.ContractRules;
import com.bank.esps.domain.model.LotAllocationResult;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.TaxLot;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class LotLogic {
    
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
                .build();
        
        state.addLot(newLot);
        
        LotAllocationResult result = new LotAllocationResult();
        result.addAllocation(lotId, qty, price);
        
        log.debug("Added new lot: {} with qty: {}, price: {}", lotId, qty, price);
        return result;
    }
    
    /**
     * Reduce lots based on contract rules (FIFO/LIFO/HIFO)
     */
    public LotAllocationResult reduceLots(PositionState state, BigDecimal qtyToReduce, ContractRules rules) {
        if (qtyToReduce.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity to reduce must be positive");
        }
        
        BigDecimal remainingQty = qtyToReduce;
        LotAllocationResult result = new LotAllocationResult();
        List<TaxLot> openLots = state.getOpenLots();
        
        if (openLots.isEmpty()) {
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
            
            // Record allocation
            result.addAllocation(lot.getId(), reduceFromLot, lot.getCurrentRefPrice());
            
            // Reduce lot quantity
            TaxLot reducedLot = lot.reduceQty(reduceFromLot);
            state.updateLot(reducedLot);
            
            // Remove if fully closed
            if (reducedLot.isClosed()) {
                state.removeLot(reducedLot.getId());
            }
            
            remainingQty = remainingQty.subtract(reduceFromLot);
            
            log.debug("Reduced lot: {} by qty: {}, remaining in lot: {}", 
                    lot.getId(), reduceFromLot, reducedLot.getRemainingQty());
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
