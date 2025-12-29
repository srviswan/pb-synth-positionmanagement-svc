package com.bank.esps.application.service;

import com.bank.esps.domain.model.LotAllocationResult;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.TaxLot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Tax lot allocation logic (FIFO/LIFO)
 */
@Component
public class LotLogic {
    
    private static final Logger log = LoggerFactory.getLogger(LotLogic.class);
    
    public enum TaxLotMethod {
        FIFO,  // First-In-First-Out
        LIFO   // Last-In-First-Out
    }
    
    /**
     * Add a new tax lot to the position
     */
    public LotAllocationResult addLot(PositionState state, BigDecimal quantity, BigDecimal price, 
                                      LocalDate tradeDate, LocalDate settlementDate) {
        TaxLot newLot = TaxLot.builder()
                .lotId(UUID.randomUUID().toString())
                .originalQty(quantity)
                .remainingQty(quantity)
                .costBasis(price)
                .currentRefPrice(price)
                .tradeDate(tradeDate)
                .settlementDate(settlementDate)
                .settledQuantity(quantity)
                .build();
        
        state.getOpenLots().add(newLot);
        
        LotAllocationResult result = new LotAllocationResult();
        result.addAllocation(newLot.getLotId(), quantity);
        result.setFullyAllocated(true);
        
        log.debug("Added new tax lot: lotId={}, qty={}, price={}", newLot.getLotId(), quantity, price);
        return result;
    }
    
    /**
     * Reduce lots using FIFO or LIFO method
     */
    public LotAllocationResult reduceLots(PositionState state, BigDecimal qtyToReduce, 
                                          TaxLotMethod method, BigDecimal closePrice) {
        LotAllocationResult result = new LotAllocationResult();
        BigDecimal remainingToReduce = qtyToReduce;
        
        // Get open lots sorted by method
        List<TaxLot> sortedLots = sortLotsByMethod(state.getOpenLots(), method);
        
        // Reduce lots in order
        for (TaxLot lot : sortedLots) {
            if (remainingToReduce.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            
            BigDecimal lotRemaining = lot.getRemainingQty() != null ? lot.getRemainingQty() : BigDecimal.ZERO;
            if (lotRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Skip fully allocated lots
            }
            
            BigDecimal qtyToReduceFromLot = remainingToReduce.min(lotRemaining);
            BigDecimal newRemaining = lotRemaining.subtract(qtyToReduceFromLot);
            
            lot.setRemainingQty(newRemaining);
            result.addAllocation(lot.getLotId(), qtyToReduceFromLot);
            remainingToReduce = remainingToReduce.subtract(qtyToReduceFromLot);
            
            log.debug("Reduced lot: lotId={}, reduced={}, remaining={}", 
                    lot.getLotId(), qtyToReduceFromLot, newRemaining);
        }
        
        // Remove fully allocated lots
        state.getOpenLots().removeIf(lot -> {
            BigDecimal remaining = lot.getRemainingQty() != null ? lot.getRemainingQty() : BigDecimal.ZERO;
            return remaining.compareTo(BigDecimal.ZERO) <= 0;
        });
        
        result.setRemainingQtyToAllocate(remainingToReduce);
        result.setFullyAllocated(remainingToReduce.compareTo(BigDecimal.ZERO) <= 0);
        
        if (!result.isFullyAllocated()) {
            log.warn("Could not fully reduce quantity. Requested: {}, Remaining: {}", 
                    qtyToReduce, remainingToReduce);
        }
        
        return result;
    }
    
    /**
     * Sort lots by FIFO or LIFO method
     */
    private List<TaxLot> sortLotsByMethod(List<TaxLot> lots, TaxLotMethod method) {
        List<TaxLot> sorted = new ArrayList<>(lots);
        
        if (method == TaxLotMethod.FIFO) {
            // FIFO: Oldest trade date first
            sorted.sort(Comparator
                    .comparing((TaxLot lot) -> lot.getTradeDate() != null ? lot.getTradeDate() : LocalDate.MAX)
                    .thenComparing(lot -> lot.getLotId()));
        } else if (method == TaxLotMethod.LIFO) {
            // LIFO: Newest trade date first
            sorted.sort(Comparator
                    .comparing((TaxLot lot) -> lot.getTradeDate() != null ? lot.getTradeDate() : LocalDate.MIN, 
                            Comparator.reverseOrder())
                    .thenComparing(lot -> lot.getLotId(), Comparator.reverseOrder()));
        }
        
        return sorted;
    }
}
