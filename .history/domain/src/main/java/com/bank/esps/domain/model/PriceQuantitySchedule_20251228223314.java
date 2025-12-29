package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * PriceQuantity Schedule (CDM-inspired)
 * Tracks quantity and price pairs over time
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceQuantitySchedule {
    @Builder.Default
    private List<DatedPriceQuantity> schedule = new ArrayList<>();
    private String unit; // "SHARES", "CONTRACTS", etc.
    private String currency; // "USD", "EUR", etc.
    
    /**
     * Dated price/quantity pair
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatedPriceQuantity {
        private LocalDate effectiveDate;
        private BigDecimal quantity;
        private BigDecimal price;
        
        /**
         * Calculate notional (quantity × price)
         */
        public BigDecimal getNotional() {
            if (quantity == null || price == null) {
                return BigDecimal.ZERO;
            }
            return quantity.multiply(price);
        }
    }
    
    /**
     * Add or update schedule entry for a date
     */
    public void addOrUpdateEntry(LocalDate effectiveDate, BigDecimal quantity, BigDecimal price) {
        // Remove existing entry for this date if present
        schedule.removeIf(entry -> entry.getEffectiveDate() != null && 
                                  entry.getEffectiveDate().equals(effectiveDate));
        
        // Add new entry
        schedule.add(DatedPriceQuantity.builder()
                .effectiveDate(effectiveDate)
                .quantity(quantity)
                .price(price)
                .build());
        
        // Sort by effective date
        schedule.sort((a, b) -> {
            if (a.getEffectiveDate() == null && b.getEffectiveDate() == null) return 0;
            if (a.getEffectiveDate() == null) return 1;
            if (b.getEffectiveDate() == null) return -1;
            return a.getEffectiveDate().compareTo(b.getEffectiveDate());
        });
    }
    
    /**
     * Get latest entry
     */
    public DatedPriceQuantity getLatestEntry() {
        if (schedule == null || schedule.isEmpty()) {
            return null;
        }
        return schedule.get(schedule.size() - 1);
    }
}
