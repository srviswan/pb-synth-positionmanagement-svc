package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a tax lot within a position
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxLot {
    private String lotId;
    private BigDecimal originalQty;
    private BigDecimal remainingQty;
    private BigDecimal costBasis;
    private BigDecimal currentRefPrice;
    private LocalDate tradeDate;
    private LocalDate settlementDate;
    private BigDecimal settledQuantity;
    
    /**
     * Calculate remaining notional (quantity × price)
     */
    public BigDecimal getRemainingNotional() {
        BigDecimal qty = remainingQty != null ? remainingQty : BigDecimal.ZERO;
        BigDecimal price = currentRefPrice != null ? currentRefPrice : BigDecimal.ZERO;
        return qty.multiply(price);
    }
    
    /**
     * Calculate realized P&L when closing this lot
     * For long positions: P&L = (closePrice - costBasis) × closedQty
     * For short positions: P&L = (costBasis - closePrice) × closedQty
     */
    public BigDecimal calculateRealizedPnL(BigDecimal closePrice, BigDecimal closedQty, boolean isShort) {
        if (closePrice == null || closedQty == null || costBasis == null) {
            return BigDecimal.ZERO;
        }
        
        if (isShort) {
            // Short position: P&L = (costBasis - closePrice) × closedQty
            return costBasis.subtract(closePrice).multiply(closedQty);
        } else {
            // Long position: P&L = (closePrice - costBasis) × closedQty
            return closePrice.subtract(costBasis).multiply(closedQty);
        }
    }
}
