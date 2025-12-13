package com.bank.esps.application.validation;

import com.bank.esps.domain.event.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Validation Gate - Early-stage validation before Kafka write
 * Prevents dirty data from entering the system
 */
@Service
public class TradeValidationService {
    
    private static final Logger log = LoggerFactory.getLogger(TradeValidationService.class);
    
    /**
     * Validate trade event
     * Returns list of validation errors (empty if valid)
     */
    public ValidationResult validate(TradeEvent trade) {
        List<String> errors = new ArrayList<>();
        
        // Required fields validation
        if (trade.getTradeId() == null || trade.getTradeId().isEmpty()) {
            errors.add("Trade ID is required");
        }
        
        if (trade.getPositionKey() == null || trade.getPositionKey().isEmpty()) {
            errors.add("Position key is required");
        }
        
        if (trade.getTradeType() == null || trade.getTradeType().isEmpty()) {
            errors.add("Trade type is required");
        }
        
        if (trade.getQuantity() == null) {
            errors.add("Quantity is required");
        } else if (trade.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Quantity must be positive");
        }
        
        if (trade.getPrice() == null) {
            errors.add("Price is required");
        } else if (trade.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Price must be positive");
        }
        
        if (trade.getEffectiveDate() == null) {
            errors.add("Effective date is required");
        } else {
            // Business date validation (not too far in future)
            LocalDate maxFutureDate = LocalDate.now().plusYears(1);
            if (trade.getEffectiveDate().isAfter(maxFutureDate)) {
                errors.add("Effective date cannot be more than 1 year in the future");
            }
        }
        
        // Position key format validation (should be hash format)
        if (trade.getPositionKey() != null && !trade.getPositionKey().matches("^[a-fA-F0-9]{32,64}$")) {
            errors.add("Position key must be a valid hash (32-64 hex characters)");
        }
        
        // Trade type validation
        if (trade.getTradeType() != null && 
            !trade.getTradeType().matches("^(NEW_TRADE|INCREASE|DECREASE)$")) {
            errors.add("Trade type must be NEW_TRADE, INCREASE, or DECREASE");
        }
        
        // Price validation (within reasonable bounds)
        if (trade.getPrice() != null) {
            BigDecimal maxPrice = new BigDecimal("1000000"); // $1M per unit
            if (trade.getPrice().compareTo(maxPrice) > 0) {
                errors.add("Price exceeds maximum allowed value");
            }
        }
        
        boolean isValid = errors.isEmpty();
        if (!isValid) {
            log.warn("Trade validation failed for trade {}: {}", trade.getTradeId(), errors);
        } else {
            log.debug("Trade validation passed for trade {}", trade.getTradeId());
        }
        
        ValidationResult result = new ValidationResult();
        result.setValid(isValid);
        result.setErrors(errors);
        return result;
    }
    
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public void setErrors(List<String> errors) {
            this.errors = errors;
        }
    }
}
