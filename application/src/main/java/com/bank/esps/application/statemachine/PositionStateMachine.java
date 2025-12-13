package com.bank.esps.application.statemachine;

import com.bank.esps.domain.enums.PositionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * State machine for position lifecycle management
 * 
 * Manages transitions between position states:
 * - NON_EXISTENT: Position doesn't exist
 * - ACTIVE: Position has quantity > 0
 * - TERMINATED: Position quantity = 0
 * 
 * This centralizes state transition logic used by both hotpath and coldpath.
 */
@Component
public class PositionStateMachine {
    
    private static final Logger log = LoggerFactory.getLogger(PositionStateMachine.class);
    
    /**
     * Represents the current state of a position
     */
    public enum State {
        NON_EXISTENT,  // Position doesn't exist yet
        ACTIVE,        // Position has open quantity > 0
        TERMINATED     // Position quantity = 0 (closed)
    }
    
    /**
     * Events that can trigger state transitions
     */
    public enum Event {
        NEW_TRADE,     // Creates new position or reopens terminated position
        INCREASE,      // Adds quantity to position
        DECREASE       // Reduces quantity (may close position)
    }
    
    /**
     * Result of a state transition attempt
     */
    public static class TransitionResult {
        private final State newState;
        private final boolean valid;
        private final String errorMessage;
        private final boolean stateChanged;
        
        private TransitionResult(State newState, boolean valid, String errorMessage, boolean stateChanged) {
            this.newState = newState;
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.stateChanged = stateChanged;
        }
        
        public static TransitionResult success(State newState, boolean stateChanged) {
            return new TransitionResult(newState, true, null, stateChanged);
        }
        
        public static TransitionResult failure(String errorMessage) {
            return new TransitionResult(null, false, errorMessage, false);
        }
        
        public State getNewState() {
            return newState;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public boolean isStateChanged() {
            return stateChanged;
        }
    }
    
    /**
     * Attempt to transition from current state based on event and resulting quantity
     * 
     * @param currentState Current position state (or NON_EXISTENT if position doesn't exist)
     * @param event Trade event type
     * @param quantityAfter Quantity after the trade is applied
     * @return Transition result indicating new state and validity
     */
    public TransitionResult transition(State currentState, Event event, BigDecimal quantityAfter) {
        log.debug("State transition: {} + {} -> quantity: {}", currentState, event, quantityAfter);
        
        // Validate quantity
        if (quantityAfter == null) {
            return TransitionResult.failure("Quantity after trade cannot be null");
        }
        
        // Handle NON_EXISTENT state
        if (currentState == State.NON_EXISTENT) {
            if (event == Event.NEW_TRADE) {
                // NEW_TRADE creates a new ACTIVE position
                return TransitionResult.success(State.ACTIVE, true);
            }
            return TransitionResult.failure(
                String.format("Only NEW_TRADE allowed on non-existent position. Received: %s", event));
        }
        
        // Handle ACTIVE state
        if (currentState == State.ACTIVE) {
            if (event == Event.NEW_TRADE) {
                return TransitionResult.failure(
                    "NEW_TRADE not allowed on ACTIVE position. Use INCREASE or DECREASE to modify the position.");
            }
            
            if (event == Event.INCREASE) {
                // INCREASE keeps position ACTIVE
                return TransitionResult.success(State.ACTIVE, false);
            }
            
            if (event == Event.DECREASE) {
                // DECREASE may close the position if quantity becomes zero
                if (quantityAfter.compareTo(BigDecimal.ZERO) == 0) {
                    return TransitionResult.success(State.TERMINATED, true);
                }
                // Otherwise stays ACTIVE
                return TransitionResult.success(State.ACTIVE, false);
            }
        }
        
        // Handle TERMINATED state
        if (currentState == State.TERMINATED) {
            if (event == Event.NEW_TRADE) {
                // NEW_TRADE reopens a TERMINATED position
                return TransitionResult.success(State.ACTIVE, true);
            }
            return TransitionResult.failure(
                String.format("Only NEW_TRADE allowed on TERMINATED position (reopening). Received: %s", event));
        }
        
        // Should never reach here
        return TransitionResult.failure(
            String.format("Invalid state transition: %s + %s", currentState, event));
    }
    
    /**
     * Convert PositionStatus enum to State enum
     */
    public State fromPositionStatus(PositionStatus status) {
        if (status == null) {
            return State.NON_EXISTENT;
        }
        return switch (status) {
            case ACTIVE -> State.ACTIVE;
            case TERMINATED -> State.TERMINATED;
        };
    }
    
    /**
     * Convert State enum to PositionStatus enum
     */
    public PositionStatus toPositionStatus(State state) {
        if (state == null || state == State.NON_EXISTENT) {
            return null; // No position status for non-existent
        }
        return switch (state) {
            case ACTIVE -> PositionStatus.ACTIVE;
            case TERMINATED -> PositionStatus.TERMINATED;
            case NON_EXISTENT -> null;
        };
    }
    
    /**
     * Convert trade type string to Event enum
     */
    public Event fromTradeType(String tradeType) {
        if (tradeType == null) {
            return null;
        }
        return switch (tradeType) {
            case "NEW_TRADE" -> Event.NEW_TRADE;
            case "INCREASE" -> Event.INCREASE;
            case "DECREASE" -> Event.DECREASE;
            default -> null;
        };
    }
    
    /**
     * Get current state based on position existence and status
     * 
     * @param positionExists Whether position exists in database
     * @param currentStatus Current position status (null if doesn't exist)
     * @return Current state
     */
    public State getCurrentState(boolean positionExists, PositionStatus currentStatus) {
        if (!positionExists) {
            return State.NON_EXISTENT;
        }
        return fromPositionStatus(currentStatus);
    }
}
