package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PriceQuantity Schedule (CDM-inspired)
 * Represents scheduled quantity and price pairs over time
 * Similar to CDM's PriceQuantity with MeasureSchedule
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceQuantitySchedule {
    
    /**
     * List of dated quantity/price pairs
     * Each entry represents the quantity and price at a specific date
     */
    @Builder.Default
    private List<DatedPriceQuantity> schedule = new ArrayList<>();
    
    /**
     * Unit of measurement (e.g., "SHARES", "CONTRACTS")
     */
    private String unit;
    
    /**
     * Currency for price (e.g., "USD", "EUR")
     */
    private String currency;
    
    /**
     * Dated price and quantity entry
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatedPriceQuantity {
        /**
         * Effective date for this quantity/price pair
         */
        private LocalDate effectiveDate;
        
        /**
         * Quantity at this date
         */
        private BigDecimal quantity;
        
        /**
         * Price per unit at this date
         */
        private BigDecimal price;
        
        /**
         * Notional value (quantity × price)
         */
        public BigDecimal getNotional() {
            if (quantity == null || price == null) {
                return BigDecimal.ZERO;
            }
            return quantity.multiply(price);
        }
    }
    
    /**
     * Add a dated quantity/price entry
     * If entry for date exists, update it; otherwise add new
     */
    public void addOrUpdate(LocalDate date, BigDecimal quantity, BigDecimal price) {
        // Remove existing entry for this date if present
        schedule.removeIf(entry -> entry.getEffectiveDate().equals(date));
        
        // Add new entry
        schedule.add(DatedPriceQuantity.builder()
                .effectiveDate(date)
                .quantity(quantity)
                .price(price)
                .build());
        
        // Sort by date
        schedule.sort((a, b) -> a.getEffectiveDate().compareTo(b.getEffectiveDate()));
    }
    
    /**
     * Get quantity/price for a specific date
     * Returns the most recent entry on or before the given date
     */
    public DatedPriceQuantity getForDate(LocalDate date) {
        return schedule.stream()
                .filter(entry -> !entry.getEffectiveDate().isAfter(date))
                .max((a, b) -> a.getEffectiveDate().compareTo(b.getEffectiveDate()))
                .orElse(null);
    }
    
    /**
     * Get current quantity/price (most recent entry)
     */
    public DatedPriceQuantity getCurrent() {
        if (schedule.isEmpty()) {
            return null;
        }
        return schedule.get(schedule.size() - 1);
    }
    
    /**
     * Get total notional across all schedule entries
     */
    public BigDecimal getTotalNotional() {
        return schedule.stream()
                .map(DatedPriceQuantity::getNotional)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Get schedule entries within date range
     */
    public List<DatedPriceQuantity> getRange(LocalDate fromDate, LocalDate toDate) {
        return schedule.stream()
                .filter(entry -> !entry.getEffectiveDate().isBefore(fromDate) && 
                                !entry.getEffectiveDate().isAfter(toDate))
                .collect(Collectors.toList());
    }
}
