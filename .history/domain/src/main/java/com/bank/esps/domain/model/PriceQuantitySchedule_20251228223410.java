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
 * PriceQuantity Schedule (CDM-inspired, Hybrid Approach)
 * Tracks quantity and price pairs over time with both trade date and settlement date.
 * 
 * Hybrid Approach:
 * - Trade Date: When trade was executed (for position tracking)
 * - Settlement Date: When quantity was actually settled (for interest accrual)
 * - Effective Date: Settlement date (used for interest accrual calculations)
 * - Settled Quantity: Quantity that was actually settled (may differ from effective quantity)
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
     * Dated price/quantity pair with hybrid settlement tracking
     * 
     * For interest accrual:
     * - Use settlementDate (when interest accrual starts)
     * - Use settledQuantity (quantity that was actually settled)
     * 
     * For position tracking:
     * - Use tradeDate (when trade was executed)
     * - Use quantity (effective quantity at trade date)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatedPriceQuantity {
        /**
         * Trade Date: When trade was executed (for position tracking)
         */
        private LocalDate tradeDate;
        
        /**
         * Settlement Date: When quantity was actually settled (for interest accrual)
         * This is the primary date used for interest calculations.
         * If not provided, falls back to tradeDate.
         */
        private LocalDate settlementDate;
        
        /**
         * Effective Date: Settlement date (used for interest accrual calculations)
         * In CDM terms, this is PriceQuantity.effectiveDate
         * Defaults to settlementDate if not explicitly set
         */
        private LocalDate effectiveDate;
        
        /**
         * Quantity: Effective quantity at trade date (for position tracking)
         */
        private BigDecimal quantity;
        
        /**
         * Settled Quantity: Quantity that was actually settled (for interest accrual)
         * May differ from quantity if partial settlement occurred.
         * If not provided, defaults to quantity.
         */
        private BigDecimal settledQuantity;
        
        /**
         * Price: Price at which quantity was traded
         */
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
        
        /**
         * Calculate settled notional (settledQuantity × price)
         * Used for interest accrual calculations
         */
        public BigDecimal getSettledNotional() {
            BigDecimal qty = settledQuantity != null ? settledQuantity : quantity;
            if (qty == null || price == null) {
                return BigDecimal.ZERO;
            }
            return qty.multiply(price);
        }
        
        /**
         * Get effective date for interest accrual
         * Priority: effectiveDate > settlementDate > tradeDate
         */
        public LocalDate getAccrualStartDate() {
            if (effectiveDate != null) {
                return effectiveDate;
            }
            if (settlementDate != null) {
                return settlementDate;
            }
            return tradeDate;
        }
        
        /**
         * Get settled quantity for interest accrual
         * Falls back to quantity if settledQuantity not provided
         */
        public BigDecimal getSettledQuantityForAccrual() {
            return settledQuantity != null ? settledQuantity : quantity;
        }
    }
    
    /**
     * Add or update schedule entry with hybrid approach
     * 
     * @param tradeDate Trade date (when trade was executed)
     * @param settlementDate Settlement date (when interest accrual starts) - can be null, falls back to tradeDate
     * @param quantity Effective quantity at trade date
     * @param settledQuantity Quantity that was actually settled (can be null, falls back to quantity)
     * @param price Price at which quantity was traded
     */
    public void addOrUpdateEntry(LocalDate tradeDate, LocalDate settlementDate, 
                                 BigDecimal quantity, BigDecimal settledQuantity, BigDecimal price) {
        // Use settlement date as effective date (for interest accrual)
        LocalDate effectiveDate = settlementDate != null ? settlementDate : tradeDate;
        
        // Remove existing entry for this trade date if present
        schedule.removeIf(entry -> entry.getTradeDate() != null && 
                                  entry.getTradeDate().equals(tradeDate));
        
        // Add new entry
        schedule.add(DatedPriceQuantity.builder()
                .tradeDate(tradeDate)
                .settlementDate(settlementDate)
                .effectiveDate(effectiveDate) // Settlement date for interest accrual
                .quantity(quantity)
                .settledQuantity(settledQuantity != null ? settledQuantity : quantity)
                .price(price)
                .build());
        
        // Sort by settlement date (for interest accrual queries) or trade date
        schedule.sort((a, b) -> {
            LocalDate dateA = a.getSettlementDate() != null ? a.getSettlementDate() : a.getTradeDate();
            LocalDate dateB = b.getSettlementDate() != null ? b.getSettlementDate() : b.getTradeDate();
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            return dateA.compareTo(dateB);
        });
    }
    
    /**
     * Add or update schedule entry (simplified - uses trade date as settlement date)
     * For backward compatibility
     */
    public void addOrUpdateEntry(LocalDate effectiveDate, BigDecimal quantity, BigDecimal price) {
        addOrUpdateEntry(effectiveDate, effectiveDate, quantity, null, price);
    }
    
    /**
     * Get latest entry by settlement date (for interest accrual)
     */
    public DatedPriceQuantity getLatestEntry() {
        if (schedule == null || schedule.isEmpty()) {
            return null;
        }
        return schedule.get(schedule.size() - 1);
    }
    
    /**
     * Get entry by settlement date (for interest accrual calculations)
     * Returns the entry with the latest settlement date <= asOfDate
     */
    public DatedPriceQuantity getEntryBySettlementDate(LocalDate asOfDate) {
        if (schedule == null || schedule.isEmpty() || asOfDate == null) {
            return null;
        }
        
        return schedule.stream()
                .filter(entry -> {
                    LocalDate settlementDate = entry.getSettlementDate() != null 
                            ? entry.getSettlementDate() 
                            : entry.getTradeDate();
                    return settlementDate != null && !settlementDate.isAfter(asOfDate);
                })
                .max((a, b) -> {
                    LocalDate dateA = a.getSettlementDate() != null ? a.getSettlementDate() : a.getTradeDate();
                    LocalDate dateB = b.getSettlementDate() != null ? b.getSettlementDate() : b.getTradeDate();
                    if (dateA == null && dateB == null) return 0;
                    if (dateA == null) return -1;
                    if (dateB == null) return 1;
                    return dateA.compareTo(dateB);
                })
                .orElse(null);
    }
    
    /**
     * Get total settled quantity as of a given date (for interest accrual)
     */
    public BigDecimal getTotalSettledQuantityAsOf(LocalDate asOfDate) {
        if (schedule == null || schedule.isEmpty() || asOfDate == null) {
            return BigDecimal.ZERO;
        }
        
        return schedule.stream()
                .filter(entry -> {
                    LocalDate settlementDate = entry.getSettlementDate() != null 
                            ? entry.getSettlementDate() 
                            : entry.getTradeDate();
                    return settlementDate != null && !settlementDate.isAfter(asOfDate);
                })
                .map(entry -> entry.getSettledQuantityForAccrual())
                .filter(qty -> qty != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
