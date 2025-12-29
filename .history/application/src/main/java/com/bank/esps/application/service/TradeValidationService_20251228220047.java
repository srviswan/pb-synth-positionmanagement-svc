package com.bank.esps.application.service;

import com.bank.esps.domain.event.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for validating trade events before processing
 */
@Service
public class TradeValidationService {
    
    private static final Logger log = LoggerFactory.getLogger(TradeValidationService.class);
    
    /**
     * Validate trade event and return list of validation errors
     * @return Empty list if valid, list of error messages if invalid
     */
    public List<String> validate(TradeEvent tradeEvent) {
        List<String> errors = new ArrayList<>();
        
        // Trade ID validation
        if (tradeEvent.getTradeId() == null || tradeEvent.getTradeId().trim().isEmpty()) {
            errors.add("Trade ID is required");
        }
        
        // Account validation
        if (tradeEvent.getAccount() == null || tradeEvent.getAccount().trim().isEmpty()) {
            errors.add("Account is required");
        }
        
        // Instrument validation
        if (tradeEvent.getInstrument() == null || tradeEvent.getInstrument().trim().isEmpty()) {
            errors.add("Instrument is required");
        }
        
        // Currency validation
        if (tradeEvent.getCurrency() == null || tradeEvent.getCurrency().trim().isEmpty()) {
            errors.add("Currency is required");
        }
        
        // Quantity validation
        if (tradeEvent.getQuantity() == null) {
            errors.add("Quantity is required");
        } else {
            if (tradeEvent.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                errors.add("Quantity cannot be zero");
            }
        }
        
        // Price validation
        if (tradeEvent.getPrice() == null) {
            errors.add("Price is required");
        } else {
            if (tradeEvent.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Price must be positive");
            }
            // Reasonable price bounds (configurable)
            if (tradeEvent.getPrice().compareTo(new BigDecimal("1000000")) > 0) {
                errors.add("Price exceeds maximum allowed value (1,000,000)");
            }
        }
        
        // Trade date validation
        if (tradeEvent.getTradeDate() == null) {
            errors.add("Trade date is required");
        } else {
            LocalDate today = LocalDate.now();
            // Allow trades up to 1 year in the future (configurable)
            LocalDate maxFutureDate = today.plusYears(1);
            if (tradeEvent.getTradeDate().isAfter(maxFutureDate)) {
                errors.add("Trade date cannot be more than 1 year in the future");
            }
        }
        
        // Effective date validation
        if (tradeEvent.getEffectiveDate() != null) {
            LocalDate today = LocalDate.now();
            LocalDate maxFutureDate = today.plusYears(1);
            if (tradeEvent.getEffectiveDate().isAfter(maxFutureDate)) {
                errors.add("Effective date cannot be more than 1 year in the future");
            }
        }
        
        // Position key format validation (if provided)
        if (tradeEvent.getPositionKey() != null && !tradeEvent.getPositionKey().isEmpty()) {
            if (!tradeEvent.getPositionKey().matches("^[^:]+:[^:]+:[^:]+$")) {
                errors.add("Position key must be in format: account:instrument:currency");
            }
        }
        
        if (!errors.isEmpty()) {
            log.warn("Validation failed for trade {}: {}", tradeEvent.getTradeId(), errors);
        }
        
        return errors;
    }
    
    /**
     * Check if trade is valid
     */
    public boolean isValid(TradeEvent tradeEvent) {
        return validate(tradeEvent).isEmpty();
    }
}
