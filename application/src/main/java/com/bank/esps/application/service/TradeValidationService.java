package com.bank.esps.application.service;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.PositionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for validating trade events before processing
 * Includes validation for direction boundaries (long/short transitions)
 */
@Service
public class TradeValidationService {
    
    private static final Logger log = LoggerFactory.getLogger(TradeValidationService.class);
    
    private final PositionKeyGenerator positionKeyGenerator;
    
    public TradeValidationService(PositionKeyGenerator positionKeyGenerator) {
        this.positionKeyGenerator = positionKeyGenerator;
    }
    
    /**
     * Validate trade event and return list of validation errors
     * @return Empty list if valid, list of error messages if invalid
     */
    /**
     * Validate trade event
     * @param tradeEvent Trade event to validate
     * @param currentState Current position state (optional, for direction boundary checks)
     * @return List of validation errors
     */
    public List<String> validate(TradeEvent tradeEvent, PositionState currentState) {
        List<String> errors = new ArrayList<>();
        
        // Basic validation
        errors.addAll(validateBasic(tradeEvent));
        
        // Direction boundary validation (if current state provided)
        if (currentState != null && currentState.getOpenLots() != null && !currentState.getOpenLots().isEmpty()) {
            errors.addAll(validateDirectionBoundaries(tradeEvent, currentState));
        }
        
        return errors;
    }
    
    /**
     * Basic validation (backward compatible)
     */
    public List<String> validate(TradeEvent tradeEvent) {
        return validateBasic(tradeEvent);
    }
    
    /**
     * Basic validation rules
     */
    private List<String> validateBasic(TradeEvent tradeEvent) {
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
     * Validate direction boundaries
     * Note: Sign changes are allowed (auto-transition to new position_key)
     * This validation is informational - actual transitions are handled in HotpathPositionService
     */
    private List<String> validateDirectionBoundaries(TradeEvent tradeEvent, PositionState currentState) {
        List<String> errors = new ArrayList<>();
        
        PositionKeyGenerator.Direction currentDirection = positionKeyGenerator.extractDirection(currentState);
        BigDecimal currentTotalQty = currentState.getTotalQty();
        BigDecimal tradeQty = tradeEvent.getQuantity() != null ? tradeEvent.getQuantity() : BigDecimal.ZERO;
        BigDecimal newTotalQty = currentTotalQty.add(tradeQty);
        
        // Check if trade would cause direction change
        boolean wouldChangeDirection = (currentDirection == PositionKeyGenerator.Direction.LONG && 
                                      newTotalQty.compareTo(BigDecimal.ZERO) < 0) ||
                                     (currentDirection == PositionKeyGenerator.Direction.SHORT && 
                                      newTotalQty.compareTo(BigDecimal.ZERO) > 0);
        
        if (wouldChangeDirection) {
            // This is informational - sign changes are allowed and will auto-transition
            log.debug("Trade {} would cause direction change from {} to {} (qty: {} -> {}). " +
                    "This will be handled by auto-transition to new position_key.",
                    tradeEvent.getTradeId(), currentDirection,
                    currentDirection == PositionKeyGenerator.Direction.LONG ? "SHORT" : "LONG",
                    currentTotalQty, newTotalQty);
            // Note: We don't add this as an error - sign changes are allowed
        }
        
        // Validate decrease doesn't exceed available quantity (for same direction)
        if (!wouldChangeDirection) {
            if (currentDirection == PositionKeyGenerator.Direction.LONG && tradeQty.compareTo(BigDecimal.ZERO) < 0) {
                // Decreasing long position
                BigDecimal decreaseQty = tradeQty.abs();
                if (decreaseQty.compareTo(currentTotalQty) > 0) {
                    errors.add(String.format("Decrease quantity %s exceeds available long quantity %s", 
                            decreaseQty, currentTotalQty));
                }
            } else if (currentDirection == PositionKeyGenerator.Direction.SHORT && tradeQty.compareTo(BigDecimal.ZERO) > 0) {
                // Decreasing short position (buying back)
                BigDecimal shortQty = currentTotalQty.abs(); // Current short quantity (positive)
                if (tradeQty.compareTo(shortQty) > 0) {
                    errors.add(String.format("Buy-back quantity %s exceeds available short quantity %s", 
                            tradeQty, shortQty));
                }
            }
        }
        
        return errors;
    }
    
    /**
     * Check if trade is valid
     */
    public boolean isValid(TradeEvent tradeEvent) {
        return validate(tradeEvent).isEmpty();
    }
    
    public boolean isValid(TradeEvent tradeEvent, PositionState currentState) {
        return validate(tradeEvent, currentState).isEmpty();
    }
}
