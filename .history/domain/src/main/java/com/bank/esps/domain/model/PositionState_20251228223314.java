package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the current state of a position
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionState {
    private String positionKey;
    private String account;
    private String instrument;
    private String currency;
    private List<TaxLot> openLots;
    private int version;
    private PriceQuantitySchedule priceQuantitySchedule;
    private String contractId; // Contract ID for tax lot method rules
    
    public PositionState(String positionKey, String account, String instrument, String currency) {
        this.positionKey = positionKey;
        this.account = account;
        this.instrument = instrument;
        this.currency = currency;
        this.openLots = new ArrayList<>();
        this.version = 0;
    }
    
    public BigDecimal getTotalQty() {
        return openLots.stream()
                .map(lot -> lot.getRemainingQty() != null ? lot.getRemainingQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public BigDecimal getExposure() {
        return openLots.stream()
                .map(lot -> {
                    BigDecimal qty = lot.getRemainingQty() != null ? lot.getRemainingQty() : BigDecimal.ZERO;
                    BigDecimal price = lot.getCurrentRefPrice() != null ? lot.getCurrentRefPrice() : BigDecimal.ZERO;
                    return qty.multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Calculate total realized P&L from closed lots
     * Note: This would need to be tracked separately or calculated from event history
     */
    public BigDecimal getTotalRealizedPnL() {
        // This would need to be calculated from event history or stored separately
        // For now, return zero as P&L is tracked per lot reduction
        return BigDecimal.ZERO;
    }
    
    /**
     * Get weighted average price of open lots
     */
    public BigDecimal getWeightedAveragePrice() {
        BigDecimal totalQty = getTotalQty();
        if (totalQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalValue = openLots.stream()
                .map(lot -> {
                    BigDecimal qty = lot.getRemainingQty() != null ? lot.getRemainingQty() : BigDecimal.ZERO;
                    BigDecimal price = lot.getCostBasis() != null ? lot.getCostBasis() : BigDecimal.ZERO;
                    return qty.multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return totalValue.divide(totalQty, 4, java.math.RoundingMode.HALF_UP);
    }
}
