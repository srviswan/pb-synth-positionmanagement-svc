package com.bank.esps.application.service;

import com.bank.esps.domain.enums.EventType;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.ContractRules;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.esps.domain.model.CompressedLots;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotpathPositionServiceTest {
    
    @Mock
    private EventStoreRepository eventStoreRepository;
    
    @Mock
    private SnapshotRepository snapshotRepository;
    
    @Mock
    private LotLogic lotLogic;
    
    @Mock
    private ContractRulesService contractRulesService;
    
    @Mock
    private IdempotencyService idempotencyService;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private HotpathPositionService hotpathService;
    
    @Test
    void testProcessNewTrade() {
        // Given
        TradeEvent trade = TradeEvent.builder()
                .tradeId("T123")
                .positionKey("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6")
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("50.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C1")
                .correlationId("CORR-123")
                .build();
        
        // No existing snapshot
        when(snapshotRepository.findById(anyString())).thenReturn(Optional.empty());
        when(contractRulesService.getContractRules(anyString())).thenReturn(
                ContractRules.defaultRules("C1")
        );
        when(eventStoreRepository.save(any(EventEntity.class))).thenAnswer(invocation -> {
            EventEntity event = invocation.getArgument(0);
            return event;
        });
        when(snapshotRepository.save(any(SnapshotEntity.class))).thenAnswer(invocation -> {
            SnapshotEntity snapshot = invocation.getArgument(0);
            return snapshot;
        });
        
        // When
        assertDoesNotThrow(() -> hotpathService.processCurrentDatedTrade(trade));
        
        // Then
        verify(eventStoreRepository, times(1)).save(any(EventEntity.class));
        verify(snapshotRepository, times(1)).save(any(SnapshotEntity.class));
        verify(idempotencyService, times(1)).markAsProcessed(eq(trade), anyLong());
    }
    
    @Test
    void testProcessIncreaseTrade() {
        // Given
        TradeEvent trade = TradeEvent.builder()
                .tradeId("T124")
                .positionKey("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6")
                .tradeType("INCREASE")
                .quantity(new BigDecimal("50"))
                .price(new BigDecimal("55.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C1")
                .build();
        
        SnapshotEntity existingSnapshot = new SnapshotEntity();
        existingSnapshot.setPositionKey("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6");
        existingSnapshot.setLastVer(5L);
        existingSnapshot.setVersion(0L);
        
        when(snapshotRepository.findById(anyString())).thenReturn(Optional.of(existingSnapshot));
        when(contractRulesService.getContractRules(anyString())).thenReturn(
                ContractRules.defaultRules("C1")
        );
        when(eventStoreRepository.save(any(EventEntity.class))).thenAnswer(invocation -> {
            EventEntity event = invocation.getArgument(0);
            return event;
        });
        when(snapshotRepository.save(any(SnapshotEntity.class))).thenAnswer(invocation -> {
            SnapshotEntity snapshot = invocation.getArgument(0);
            return snapshot;
        });
        
        // When
        assertDoesNotThrow(() -> hotpathService.processCurrentDatedTrade(trade));
        
        // Then
        ArgumentCaptor<EventEntity> eventCaptor = ArgumentCaptor.forClass(EventEntity.class);
        verify(eventStoreRepository).save(eventCaptor.capture());
        
        EventEntity savedEvent = eventCaptor.getValue();
        assertEquals(6L, savedEvent.getEventVer()); // Should be lastVer + 1
        assertEquals(EventType.INCREASE, savedEvent.getEventType()); // INCREASE trade type
    }
}
