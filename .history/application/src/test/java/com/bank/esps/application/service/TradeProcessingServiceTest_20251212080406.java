package com.bank.esps.application.service;

import com.bank.esps.application.validation.TradeValidationService;
import com.bank.esps.domain.enums.TradeSequenceStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.kafka.TradeEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeProcessingServiceTest {
    
    @Mock
    private TradeClassifier tradeClassifier;
    
    @Mock
    private TradeValidationService validationService;
    
    @Mock
    private IdempotencyService idempotencyService;
    
    @Mock
    private HotpathPositionService hotpathService;
    
    @Mock
    private TradeEventProducer eventProducer;
    
    @InjectMocks
    private TradeProcessingService tradeProcessingService;
    
    @Test
    void testProcessValidCurrentDatedTrade() {
        // Given
        TradeEvent trade = createValidTrade();
        
        TradeValidationService.ValidationResult validResult = new TradeValidationService.ValidationResult();
        validResult.setValid(true);
        when(validationService.validate(any())).thenReturn(validResult);
        when(idempotencyService.isProcessed(anyString())).thenReturn(false);
        when(tradeClassifier.classifyTrade(any())).thenReturn(TradeSequenceStatus.CURRENT_DATED);
        
        // When
        tradeProcessingService.processTrade(trade);
        
        // Then
        verify(hotpathService, times(1)).processCurrentDatedTrade(trade);
        verify(eventProducer, never()).publishBackdatedTrade(any());
    }
    
    @Test
    void testProcessBackdatedTrade() {
        // Given
        TradeEvent trade = createValidTrade();
        
        TradeValidationService.ValidationResult validResult2 = new TradeValidationService.ValidationResult();
        validResult2.setValid(true);
        when(validationService.validate(any())).thenReturn(validResult2);
        when(idempotencyService.isProcessed(anyString())).thenReturn(false);
        when(tradeClassifier.classifyTrade(any())).thenReturn(TradeSequenceStatus.BACKDATED);
        
        // When
        tradeProcessingService.processTrade(trade);
        
        // Then
        verify(hotpathService, never()).processCurrentDatedTrade(any());
        verify(eventProducer, times(1)).publishBackdatedTrade(trade);
    }
    
    @Test
    void testProcessInvalidTrade() {
        // Given
        TradeEvent trade = createValidTrade();
        
        TradeValidationService.ValidationResult invalidResult = new TradeValidationService.ValidationResult();
        invalidResult.setValid(false);
        invalidResult.setErrors(java.util.List.of("Invalid quantity"));
        when(validationService.validate(any())).thenReturn(invalidResult);
        
        // When
        tradeProcessingService.processTrade(trade);
        
        // Then
        verify(eventProducer, times(1)).publishToDLQ(eq(trade), anyString());
        verify(hotpathService, never()).processCurrentDatedTrade(any());
    }
    
    @Test
    void testProcessDuplicateTrade() {
        // Given
        TradeEvent trade = createValidTrade();
        
        TradeValidationService.ValidationResult validResult3 = new TradeValidationService.ValidationResult();
        validResult3.setValid(true);
        when(validationService.validate(any())).thenReturn(validResult3);
        when(idempotencyService.isProcessed(anyString())).thenReturn(true);
        
        // When
        tradeProcessingService.processTrade(trade);
        
        // Then
        verify(hotpathService, never()).processCurrentDatedTrade(any());
        verify(eventProducer, never()).publishBackdatedTrade(any());
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
