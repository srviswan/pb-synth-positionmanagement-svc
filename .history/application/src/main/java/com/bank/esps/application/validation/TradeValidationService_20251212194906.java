package com.bank.esps.application.validation;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.enums.PositionStatus;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
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
    
    private final SnapshotRepository snapshotRepository;
    
    public TradeValidationService(SnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }
    
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
        // Allow any non-empty string for now (can be made stricter later)
        if (trade.getPositionKey() != null && trade.getPositionKey().isEmpty()) {
            errors.add("Position key cannot be empty");
        }
        
        // Trade type validation
        if (trade.getTradeType() != null && 
            !trade.getTradeType().matches("^(NEW_TRADE|INCREASE|DECREASE)$")) {
            errors.add("Trade type must be NEW_TRADE, INCREASE, or DECREASE");
        }
        
        // Position state validation: Trade type must match position status
        if (trade.getPositionKey() != null && trade.getTradeType() != null) {
            var snapshotOpt = snapshotRepository.findById(trade.getPositionKey());
            
            if (snapshotOpt.isPresent()) {
                // Position exists - check status
                var snapshot = snapshotOpt.get();
                var positionStatus = snapshot.getStatus();
                
                if ("NEW_TRADE".equals(trade.getTradeType())) {
                    // NEW_TRADE should only be allowed on TERMINATED positions (reopening)
                    if (positionStatus == PositionStatus.ACTIVE) {
                        errors.add(String.format(
                            "NEW_TRADE not allowed on ACTIVE position. Position '%s' is ACTIVE. Use INCREASE or DECREASE to modify the position.",
                            trade.getPositionKey()));
                        log.warn("Validation failed: NEW_TRADE attempted on ACTIVE position {}", trade.getPositionKey());
                    }
                    // NEW_TRADE is allowed on TERMINATED position (reopening) - no error
                } else if ("INCREASE".equals(trade.getTradeType()) || "DECREASE".equals(trade.getTradeType())) {
                    // INCREASE/DECREASE should only be allowed on ACTIVE positions
                    if (positionStatus == PositionStatus.TERMINATED) {
                        errors.add(String.format(
                            "%s not allowed on TERMINATED position. Position '%s' is TERMINATED. Use NEW_TRADE to reopen the position.",
                            trade.getTradeType(), trade.getPositionKey()));
                        log.warn("Validation failed: {} attempted on TERMINATED position {}", 
                                trade.getTradeType(), trade.getPositionKey());
                    }
                }
            } else {
                // Position doesn't exist - only NEW_TRADE is allowed
                if (!"NEW_TRADE".equals(trade.getTradeType())) {
                    errors.add(String.format(
                        "Position '%s' does not exist. Only NEW_TRADE is allowed for new positions. Received: %s",
                        trade.getPositionKey(), trade.getTradeType()));
                    log.warn("Validation failed: {} attempted on non-existent position {}", 
                            trade.getTradeType(), trade.getPositionKey());
                }
            }
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
