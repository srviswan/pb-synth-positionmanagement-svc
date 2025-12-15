package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a tax lot with remaining quantity and current reference price
 * Tracks cost basis for P&L calculation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxLot {
    private String id;
    private LocalDate tradeDate;
    private BigDecimal currentRefPrice; // Updates on Reset
    private BigDecimal remainingQty;
    private BigDecimal originalQty; // Original quantity when lot was created (for P&L calculation)
    private BigDecimal originalPrice; // Original price (cost basis) when lot was created
    
    /**
     * Reduce quantity from this lot
     */
    public TaxLot reduceQty(BigDecimal qty) {
        if (qty.compareTo(remainingQty) > 0) {
            throw new IllegalArgumentException("Cannot reduce more quantity than remaining");
        }
        return TaxLot.builder()
                .id(this.id)
                .tradeDate(this.tradeDate)
                .currentRefPrice(this.currentRefPrice)
                .remainingQty(this.remainingQty.subtract(qty))
                .originalQty(this.originalQty)
                .originalPrice(this.originalPrice)
                .build();
    }
    
    /**
     * Calculate remaining notional (remaining quantity * current reference price)
     */
    public BigDecimal getRemainingNotional() {
        if (remainingQty == null || currentRefPrice == null) {
            return BigDecimal.ZERO;
        }
        return remainingQty.multiply(currentRefPrice);
    }
    
    /**
     * Calculate realized P&L when closing a lot
     * P&L = (closePrice - originalPrice) * closedQty
     */
    public BigDecimal calculateRealizedPnL(BigDecimal closePrice, BigDecimal closedQty) {
        if (originalPrice == null || closePrice == null || closedQty == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal priceDiff = closePrice.subtract(originalPrice);
        return priceDiff.multiply(closedQty);
    }
    
    /**
     * Update reference price (for market data resets)
     */
    public TaxLot updatePrice(BigDecimal newPrice) {
        return TaxLot.builder()
                .id(this.id)
                .tradeDate(this.tradeDate)
                .currentRefPrice(newPrice)
                .remainingQty(this.remainingQty)
                .originalQty(this.originalQty)
                .originalPrice(this.originalPrice)
                .build();
    }
    
    /**
     * Check if lot is fully closed
     * For long positions: remainingQty <= 0
     * For short positions: remainingQty >= 0 (closed when it reaches zero from negative)
     */
    public boolean isClosed() {
        if (remainingQty == null) {
            return true;
        }
        // Lot is closed when it reaches zero (from either direction)
        return remainingQty.compareTo(BigDecimal.ZERO) == 0;
    }
}
