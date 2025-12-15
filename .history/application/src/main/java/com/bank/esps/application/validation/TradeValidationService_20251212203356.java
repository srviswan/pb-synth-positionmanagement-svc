package com.bank.esps.application.validation;

import com.bank.esps.application.statemachine.PositionStateMachine;
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
    private final PositionStateMachine stateMachine;
    
    public TradeValidationService(SnapshotRepository snapshotRepository, PositionStateMachine stateMachine) {
        this.snapshotRepository = snapshotRepository;
        this.stateMachine = stateMachine;
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
        } else if (trade.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            errors.add("Quantity cannot be zero");
        }
        // Note: Allow negative quantities for short positions, but sign changes are handled at processing level
        
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
        
        // Position state validation: Use state machine to validate transitions
        if (trade.getPositionKey() != null && trade.getTradeType() != null) {
            var snapshotOpt = snapshotRepository.findById(trade.getPositionKey());
            
            // Determine current state
            PositionStateMachine.State currentState;
            if (snapshotOpt.isPresent()) {
                currentState = stateMachine.fromPositionStatus(snapshotOpt.get().getStatus());
            } else {
                currentState = PositionStateMachine.State.NON_EXISTENT;
            }
            
            // Convert trade type to event
            PositionStateMachine.Event event = stateMachine.fromTradeType(trade.getTradeType());
            if (event == null) {
                errors.add(String.format("Invalid trade type: %s", trade.getTradeType()));
            } else {
                // Use state machine to validate transition
                // For validation, we use current quantity (or 0 if new position)
                BigDecimal quantityAfter = trade.getQuantity();
                if (snapshotOpt.isPresent() && trade.getTradeType().equals("DECREASE")) {
                    // For DECREASE, we can't know final quantity without processing, so use a placeholder
                    // The state machine will check if DECREASE is allowed on current state
                    quantityAfter = BigDecimal.ONE; // Placeholder - actual validation happens in processing
                }
                
                var transitionResult = stateMachine.transition(currentState, event, quantityAfter);
                
                if (!transitionResult.isValid()) {
                    errors.add(transitionResult.getErrorMessage());
                    log.warn("State machine validation failed: {} on state {} - {}", 
                            event, currentState, transitionResult.getErrorMessage());
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
