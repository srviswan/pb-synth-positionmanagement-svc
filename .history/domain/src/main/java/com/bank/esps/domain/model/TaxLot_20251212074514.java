package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a tax lot with remaining quantity and current reference price
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
                .build();
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
                .build();
    }
    
    /**
     * Check if lot is fully closed
     */
    public boolean isClosed() {
        return remainingQty.compareTo(BigDecimal.ZERO) <= 0;
    }
}
