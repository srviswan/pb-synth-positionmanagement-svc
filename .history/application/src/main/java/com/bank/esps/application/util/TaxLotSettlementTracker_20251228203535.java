package com.bank.esps.application.util;

import com.bank.esps.domain.model.PriceQuantitySchedule;
import com.bank.esps.domain.model.TaxLot;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Helper class for tracking settlement dates and quantities for TaxLot.
 * Implements the hybrid approach from settlement_date_quantity_tracking_solution.md:
 * 1. Settlement date from TaxLot (if stored)
 * 2. effectiveDate from PriceQuantitySchedule (if present)
 * 3. Trade date (fallback)
 */
public class TaxLotSettlementTracker {
    
    /**
     * Information about settled quantity for interest accrual
     */
    @Data
    @Builder
    public static class SettledQuantityInfo {
        private BigDecimal quantity;
        private LocalDate settlementDate;
    }
    
    /**
     * Get settled quantity for interest accrual.
     * Priority:
     * 1. Settlement date from TaxLot (if stored)
     * 2. effectiveDate from PriceQuantitySchedule (if present)
     * 3. Trade date (fallback)
     */
    public static SettledQuantityInfo getSettledQuantity(
            TaxLot lot,
            PriceQuantitySchedule priceQuantitySchedule,
            LocalDate calculationDate) {
        
        // Try to get settlement date from TaxLot
        LocalDate settlementDate = lot.getSettlementDate();
        BigDecimal settledQuantity = lot.getSettledQuantity();
        
        // Fallback: use PriceQuantitySchedule.effectiveDate
        if (settlementDate == null && priceQuantitySchedule != null) {
            PriceQuantitySchedule.DatedPriceQuantity datedEntry = 
                priceQuantitySchedule.getForDate(calculationDate);
            
            if (datedEntry != null && datedEntry.getEffectiveDate() != null) {
                settlementDate = datedEntry.getEffectiveDate();
                settledQuantity = datedEntry.getQuantity();
            }
        }
        
        // Final fallback: use trade date
        if (settlementDate == null) {
            settlementDate = lot.getTradeDate();
            settledQuantity = lot.getOriginalQty();
        }
        
        // If settledQuantity is still null, use original quantity
        if (settledQuantity == null) {
            settledQuantity = lot.getOriginalQty();
        }
        
        return SettledQuantityInfo.builder()
            .quantity(settledQuantity)
            .settlementDate(settlementDate)
            .build();
    }
    
    /**
     * Extract settlement date from PriceQuantitySchedule.
     * Uses the effectiveDate from the most recent schedule entry.
     */
    public static LocalDate extractSettlementDateFromSchedule(
            PriceQuantitySchedule priceQuantitySchedule) {
        
        if (priceQuantitySchedule == null || priceQuantitySchedule.getSchedule() == null) {
            return null;
        }
        
        // Get the most recent entry (current)
        PriceQuantitySchedule.DatedPriceQuantity current = priceQuantitySchedule.getCurrent();
        if (current != null && current.getEffectiveDate() != null) {
            return current.getEffectiveDate();
        }
        
        return null;
    }
    
    /**
     * Extract settled quantity from PriceQuantitySchedule for a given date.
     */
    public static BigDecimal extractSettledQuantityFromSchedule(
            PriceQuantitySchedule priceQuantitySchedule,
            LocalDate settlementDate) {
        
        if (priceQuantitySchedule == null || settlementDate == null) {
            return null;
        }
        
        PriceQuantitySchedule.DatedPriceQuantity entry = 
            priceQuantitySchedule.getForDate(settlementDate);
        
        return entry != null ? entry.getQuantity() : null;
    }
}
