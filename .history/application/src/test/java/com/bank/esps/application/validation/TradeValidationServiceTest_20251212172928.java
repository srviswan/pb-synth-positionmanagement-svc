package com.bank.esps.application.validation;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeValidationServiceTest {
    
    @Mock
    private SnapshotRepository snapshotRepository;
    
    private TradeValidationService validationService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validationService = new TradeValidationService(snapshotRepository);
    }
    
    @Test
    void testValidTrade() {
        TradeEvent trade = createValidTrade();
        
        TradeValidationService.ValidationResult result = validationService.validate(trade);
        
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }
    
    @Test
    void testMissingTradeId() {
        TradeEvent trade = createValidTrade();
        trade.setTradeId(null);
        
        TradeValidationService.ValidationResult result = validationService.validate(trade);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Trade ID is required"));
    }
    
    @Test
    void testMissingQuantity() {
        TradeEvent trade = createValidTrade();
        trade.setQuantity(null);
        
        TradeValidationService.ValidationResult result = validationService.validate(trade);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Quantity is required"));
    }
    
    @Test
    void testNegativeQuantity() {
        TradeEvent trade = createValidTrade();
        trade.setQuantity(new BigDecimal("-100"));
        
        TradeValidationService.ValidationResult result = validationService.validate(trade);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Quantity must be positive"));
    }
    
    @Test
    void testInvalidPositionKey() {
        TradeEvent trade = createValidTrade();
        trade.setPositionKey(""); // Empty position key should fail
        
        TradeValidationService.ValidationResult result = validationService.validate(trade);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Position key cannot be empty") || 
                   result.getErrors().contains("Position key is required"));
    }
    
    @Test
    void testInvalidTradeType() {
        TradeEvent trade = createValidTrade();
        trade.setTradeType("INVALID_TYPE");
        
        TradeValidationService.ValidationResult result = validationService.validate(trade);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Trade type must be NEW_TRADE, INCREASE, or DECREASE"));
    }
    
    @Test
    void testFutureDateTooFar() {
        TradeEvent trade = createValidTrade();
        trade.setEffectiveDate(LocalDate.now().plusYears(2));
        
        TradeValidationService.ValidationResult result = validationService.validate(trade);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Effective date cannot be more than 1 year in the future"));
    }
    
    @Test
    void testPriceTooHigh() {
        TradeEvent trade = createValidTrade();
        trade.setPrice(new BigDecimal("2000000")); // Exceeds $1M
        
        TradeValidationService.ValidationResult result = validationService.validate(trade);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Price exceeds maximum allowed value"));
    }
    
    private TradeEvent createValidTrade() {
        return TradeEvent.builder()
                .tradeId("T12345")
                .positionKey("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6")
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("50.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C123")
                .correlationId("CORR-123")
                .build();
    }
}
