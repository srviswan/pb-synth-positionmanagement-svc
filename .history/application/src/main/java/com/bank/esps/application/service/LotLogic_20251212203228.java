package com.bank.esps.application.service;

import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.domain.model.Contract;
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
     * Reduce lots based on contract (FIFO/LIFO/HIFO)
     * @param closePrice The price at which the lots are being closed (for P&L calculation)
     * @return LotAllocationResult with remaining quantity if sign change occurs (negative = short position created)
     */
    public LotAllocationResult reduceLots(PositionState state, BigDecimal qtyToReduce, Contract contract, BigDecimal closePrice) {
        if (qtyToReduce.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity to reduce must be positive");
        }
        
        BigDecimal currentTotalQty = state.getTotalQty();
        BigDecimal remainingQty = qtyToReduce;
        LotAllocationResult result = new LotAllocationResult();
        
        // Get all lots from state (raw list, not filtered)
        List<TaxLot> allLots = state.getAllLots();
        // Filter to only open lots (remainingQty != 0)
        // For long positions: remainingQty > 0
        // For short positions: remainingQty < 0
        List<TaxLot> openLots = allLots.stream()
                .filter(lot -> lot.getRemainingQty() != null && lot.getRemainingQty().compareTo(BigDecimal.ZERO) != 0)
                .collect(java.util.stream.Collectors.toList());
        
        if (openLots.isEmpty()) {
            // No open lots - this could be a sign change scenario
            // If we're reducing from a long position and have remaining quantity, it becomes a short
            if (currentTotalQty.compareTo(BigDecimal.ZERO) > 0 && remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                log.info("⚠️ Sign change detected: Reducing {} from long position with no open lots. Remaining {} will become short position", 
                        qtyToReduce, remainingQty);
                // Mark that we need to create a short position
                result.setRemainingQuantity(remainingQty.negate()); // Negative indicates short
                return result;
            }
            
            // For short positions reducing (which means increasing the short), we might have no lots if all were closed
            // This shouldn't happen in normal flow, but handle it
            if (currentTotalQty.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Attempting to reduce short position with no open lots. This may indicate a sign change to long.");
                // The remaining quantity would create a long position
                result.setRemainingQuantity(remainingQty); // Positive indicates long
                return result;
            }
            
            log.error("Cannot reduce quantity: no open lots available. Total lots in state: {}, open lots: {}, current total qty: {}", 
                    allLots.size(), openLots.size(), currentTotalQty);
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
        List<TaxLot> sortedLots = sortLotsByMethod(openLots, contract.getTaxLotMethod());
        
        // Reduce lots in order
        // For long positions: reduce positive quantities
        // For short positions: we're increasing (reducing the short), so we add to the negative quantity
        boolean isLongPosition = currentTotalQty.compareTo(BigDecimal.ZERO) > 0;
        
        for (TaxLot lot : sortedLots) {
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            
            BigDecimal lotQty = lot.getRemainingQty();
            
            // For long positions: reduce from positive quantity
            // For short positions: we're closing the short, so we're making the negative quantity less negative (closer to zero)
            BigDecimal reduceFromLot;
            if (isLongPosition) {
                // Long position: reduce positive quantity
                reduceFromLot = remainingQty.min(lotQty);
            } else {
                // Short position: we're closing the short by reducing the absolute value
                // lotQty is negative, remainingQty is positive (the amount we want to close)
                // We reduce the absolute value of the negative quantity
                BigDecimal lotQtyAbs = lotQty.abs();
                BigDecimal reduceAbs = remainingQty.min(lotQtyAbs);
                reduceFromLot = reduceAbs.negate(); // Keep as negative
            }
            
            // Calculate P&L for this reduction
            // For short positions, P&L calculation is inverted: (originalPrice - closePrice) * closedQty
            BigDecimal realizedPnL;
            if (isLongPosition) {
                // Long position: P&L = (closePrice - originalPrice) * closedQty
                realizedPnL = lot.calculateRealizedPnL(closePrice, reduceFromLot.abs());
            } else {
                // Short position: P&L = (originalPrice - closePrice) * closedQty (inverted)
                BigDecimal priceDiff = lot.getOriginalPrice().subtract(closePrice);
                realizedPnL = priceDiff.multiply(reduceFromLot.abs());
            }
            
            // Record allocation with P&L (use absolute value for quantity)
            result.addReduction(lot.getId(), reduceFromLot.abs(), closePrice, realizedPnL);
            
            // Calculate remaining notional before reduction
            BigDecimal notionalBefore = lot.getRemainingNotional();
            
            // Reduce lot quantity
            // For long: subtract positive quantity
            // For short: subtract negative quantity (which adds to it, making it less negative)
            TaxLot reducedLot = lot.reduceQty(reduceFromLot.abs()); // reduceQty expects positive
            if (!isLongPosition) {
                // For short positions, after reducing, we need to keep it negative
                // But reduceQty subtracts, so if lotQty was -50 and we reduce 30, we get -20
                // Actually, reduceQty does: remainingQty.subtract(qty)
                // So if remainingQty is -50 and qty is 30, we get -50 - 30 = -80 (wrong!)
                // We need to handle this differently for short positions
                // Actually, for short positions, "reducing" means making the negative quantity less negative
                // So we should ADD the reduction amount to the negative quantity
                BigDecimal newQty = lot.getRemainingQty().add(reduceFromLot.abs());
                reducedLot = TaxLot.builder()
                        .id(lot.getId())
                        .tradeDate(lot.getTradeDate())
                        .currentRefPrice(lot.getCurrentRefPrice())
                        .remainingQty(newQty)
                        .originalQty(lot.getOriginalQty())
                        .originalPrice(lot.getOriginalPrice())
                        .build();
            }
            state.updateLot(reducedLot);
            
            // Calculate remaining notional after reduction
            BigDecimal notionalAfter = reducedLot.getRemainingNotional();
            
            // Remove if fully closed (reached zero from either direction)
            if (reducedLot.isClosed()) {
                state.removeLot(reducedLot.getId());
                log.info("✅ Closed lot: {} - Realized P&L: {}, closed qty: {}, close price: {}, original price: {}", 
                        lot.getId(), realizedPnL, reduceFromLot.abs(), closePrice, lot.getOriginalPrice());
            }
            
            // Update remaining quantity to reduce
            remainingQty = remainingQty.subtract(reduceFromLot.abs());
            
            log.info("Reduced lot: {} by qty: {}, remaining: {}, notional before: {}, notional after: {}, Realized P&L: {}", 
                    lot.getId(), reduceFromLot.abs(), reducedLot.getRemainingQty(), 
                    notionalBefore, notionalAfter, realizedPnL);
        }
        
        // If we still have remaining quantity after closing all lots, this indicates a sign change
        // The remaining quantity will become a short position
        // If we still have remaining quantity after closing all lots, this indicates a sign change
        // The remaining quantity will become a short position
        if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
            log.info("⚠️ Sign change detected: After closing all {} lots, remaining quantity {} will create short position", 
                    sortedLots.size(), remainingQty);
            // Mark remaining quantity as negative to indicate short position needs to be created
            result.setRemainingQuantity(remainingQty.negate());
        } else {
            // All quantity was allocated, no sign change
            result.setRemainingQuantity(BigDecimal.ZERO);
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
