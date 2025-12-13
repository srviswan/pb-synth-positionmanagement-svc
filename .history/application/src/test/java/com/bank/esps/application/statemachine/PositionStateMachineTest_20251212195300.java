package com.bank.esps.application.statemachine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PositionStateMachine
 */
class PositionStateMachineTest {
    
    private PositionStateMachine stateMachine;
    
    @BeforeEach
    void setUp() {
        stateMachine = new PositionStateMachine();
    }
    
    @Test
    void testNewTradeOnNonExistentPosition() {
        var result = stateMachine.transition(
                PositionStateMachine.State.NON_EXISTENT,
                PositionStateMachine.Event.NEW_TRADE,
                new BigDecimal("100"));
        
        assertTrue(result.isValid());
        assertEquals(PositionStateMachine.State.ACTIVE, result.getNewState());
        assertTrue(result.isStateChanged());
    }
    
    @Test
    void testIncreaseOnNonExistentPosition() {
        var result = stateMachine.transition(
                PositionStateMachine.State.NON_EXISTENT,
                PositionStateMachine.Event.INCREASE,
                new BigDecimal("100"));
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Only NEW_TRADE allowed"));
    }
    
    @Test
    void testDecreaseOnNonExistentPosition() {
        var result = stateMachine.transition(
                PositionStateMachine.State.NON_EXISTENT,
                PositionStateMachine.Event.DECREASE,
                new BigDecimal("100"));
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Only NEW_TRADE allowed"));
    }
    
    @Test
    void testIncreaseOnActivePosition() {
        var result = stateMachine.transition(
                PositionStateMachine.State.ACTIVE,
                PositionStateMachine.Event.INCREASE,
                new BigDecimal("200"));
        
        assertTrue(result.isValid());
        assertEquals(PositionStateMachine.State.ACTIVE, result.getNewState());
        assertFalse(result.isStateChanged());
    }
    
    @Test
    void testDecreaseOnActivePositionRemainingActive() {
        var result = stateMachine.transition(
                PositionStateMachine.State.ACTIVE,
                PositionStateMachine.Event.DECREASE,
                new BigDecimal("50"));
        
        assertTrue(result.isValid());
        assertEquals(PositionStateMachine.State.ACTIVE, result.getNewState());
        assertFalse(result.isStateChanged());
    }
    
    @Test
    void testDecreaseOnActivePositionClosing() {
        var result = stateMachine.transition(
                PositionStateMachine.State.ACTIVE,
                PositionStateMachine.Event.DECREASE,
                BigDecimal.ZERO);
        
        assertTrue(result.isValid());
        assertEquals(PositionStateMachine.State.TERMINATED, result.getNewState());
        assertTrue(result.isStateChanged());
    }
    
    @Test
    void testNewTradeOnActivePosition() {
        var result = stateMachine.transition(
                PositionStateMachine.State.ACTIVE,
                PositionStateMachine.Event.NEW_TRADE,
                new BigDecimal("100"));
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("NEW_TRADE not allowed on ACTIVE position"));
    }
    
    @Test
    void testNewTradeOnTerminatedPosition() {
        var result = stateMachine.transition(
                PositionStateMachine.State.TERMINATED,
                PositionStateMachine.Event.NEW_TRADE,
                new BigDecimal("100"));
        
        assertTrue(result.isValid());
        assertEquals(PositionStateMachine.State.ACTIVE, result.getNewState());
        assertTrue(result.isStateChanged());
    }
    
    @Test
    void testIncreaseOnTerminatedPosition() {
        var result = stateMachine.transition(
                PositionStateMachine.State.TERMINATED,
                PositionStateMachine.Event.INCREASE,
                new BigDecimal("100"));
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Only NEW_TRADE allowed on TERMINATED position"));
    }
    
    @Test
    void testDecreaseOnTerminatedPosition() {
        var result = stateMachine.transition(
                PositionStateMachine.State.TERMINATED,
                PositionStateMachine.Event.DECREASE,
                BigDecimal.ZERO);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Only NEW_TRADE allowed on TERMINATED position"));
    }
    
    @Test
    void testNullQuantity() {
        var result = stateMachine.transition(
                PositionStateMachine.State.ACTIVE,
                PositionStateMachine.Event.INCREASE,
                null);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Quantity after trade cannot be null"));
    }
    
    @Test
    void testFromTradeType() {
        assertEquals(PositionStateMachine.Event.NEW_TRADE, stateMachine.fromTradeType("NEW_TRADE"));
        assertEquals(PositionStateMachine.Event.INCREASE, stateMachine.fromTradeType("INCREASE"));
        assertEquals(PositionStateMachine.Event.DECREASE, stateMachine.fromTradeType("DECREASE"));
        assertNull(stateMachine.fromTradeType("INVALID"));
        assertNull(stateMachine.fromTradeType(null));
    }
    
    @Test
    void testFromPositionStatus() {
        assertEquals(PositionStateMachine.State.ACTIVE, 
                stateMachine.fromPositionStatus(com.bank.esps.domain.enums.PositionStatus.ACTIVE));
        assertEquals(PositionStateMachine.State.TERMINATED, 
                stateMachine.fromPositionStatus(com.bank.esps.domain.enums.PositionStatus.TERMINATED));
        assertEquals(PositionStateMachine.State.NON_EXISTENT, 
                stateMachine.fromPositionStatus(null));
    }
    
    @Test
    void testToPositionStatus() {
        assertEquals(com.bank.esps.domain.enums.PositionStatus.ACTIVE, 
                stateMachine.toPositionStatus(PositionStateMachine.State.ACTIVE));
        assertEquals(com.bank.esps.domain.enums.PositionStatus.TERMINATED, 
                stateMachine.toPositionStatus(PositionStateMachine.State.TERMINATED));
        assertNull(stateMachine.toPositionStatus(PositionStateMachine.State.NON_EXISTENT));
        assertNull(stateMachine.toPositionStatus(null));
    }
    
    @Test
    void testGetCurrentState() {
        assertEquals(PositionStateMachine.State.NON_EXISTENT, 
                stateMachine.getCurrentState(false, null));
        assertEquals(PositionStateMachine.State.ACTIVE, 
                stateMachine.getCurrentState(true, com.bank.esps.domain.enums.PositionStatus.ACTIVE));
        assertEquals(PositionStateMachine.State.TERMINATED, 
                stateMachine.getCurrentState(true, com.bank.esps.domain.enums.PositionStatus.TERMINATED));
    }
}
