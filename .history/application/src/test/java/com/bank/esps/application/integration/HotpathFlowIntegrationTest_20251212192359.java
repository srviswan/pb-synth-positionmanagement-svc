package com.bank.esps.application.integration;

import com.bank.esps.application.service.*;
import com.bank.esps.application.validation.TradeValidationService;
import com.bank.esps.domain.cache.CacheService;
import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.Contract;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test for the complete hotpath flow
 * Tests: Validation -> Classification -> Processing -> Persistence
 */
@SpringBootTest(classes = {
        TradeProcessingService.class,
        TradeClassifier.class,
        TradeValidationService.class,
        IdempotencyService.class,
        HotpathPositionService.class,
        LotLogic.class,
        ContractRulesService.class,
        MetricsService.class
})
@ActiveProfiles("test")
@Transactional
class HotpathFlowIntegrationTest {
    
    @Autowired
    private TradeProcessingService tradeProcessingService;
    
    @Autowired
    private LotLogic lotLogic;
    
    @Autowired
    private ContractRulesService contractRulesService;
    
    @MockBean
    private EventStoreRepository eventStoreRepository;
    
    @MockBean
    private SnapshotRepository snapshotRepository;
    
    @MockBean
    private com.bank.esps.infrastructure.persistence.repository.IdempotencyRepository idempotencyRepository;
    
    @MockBean
    private CacheService cacheService;
    
    @MockBean
    private com.bank.esps.infrastructure.kafka.TradeEventProducer eventProducer;
    
    @MockBean
    private MetricsService metricsService;
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        // Setup mocks
        when(snapshotRepository.findById(any())).thenReturn(Optional.empty());
        when(eventStoreRepository.save(any(EventEntity.class))).thenAnswer(invocation -> {
            EventEntity event = invocation.getArgument(0);
            return event;
        });
        when(snapshotRepository.save(any(SnapshotEntity.class))).thenAnswer(invocation -> {
            SnapshotEntity snapshot = invocation.getArgument(0);
            return snapshot;
        });
        when(idempotencyRepository.existsByTradeId(any())).thenReturn(false);
        
        // Mock cache service - return empty for cache misses
        when(cacheService.get(any(String.class), any(Class.class))).thenReturn(java.util.Optional.empty());
        when(cacheService.exists(any(String.class))).thenReturn(false);
    }
    
    @Test
    void testCompleteHotpathFlow_NewTrade() {
        // Given: A valid new trade
        TradeEvent trade = TradeEvent.builder()
                .tradeId("T-INTEGRATION-001")
                .positionKey("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6")
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("50.50"))
                .effectiveDate(LocalDate.now())
                .contractId("C-INTEGRATION-001")
                .correlationId("CORR-INTEGRATION-001")
                .build();
        
        // When: Process the trade
        assertDoesNotThrow(() -> tradeProcessingService.processTrade(trade));
        
        // Then: Verify the flow completed successfully
        // (In a real integration test, we would verify database state)
        assertNotNull(trade.getSequenceStatus());
    }
    
    @Test
    void testCompleteHotpathFlow_IncreaseAndDecrease() {
        // Given: First trade (increase)
        TradeEvent increaseTrade = TradeEvent.builder()
                .tradeId("T-INTEGRATION-002")
                .positionKey("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6")
                .tradeType("INCREASE")
                .quantity(new BigDecimal("500"))
                .price(new BigDecimal("55.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-INTEGRATION-001")
                .correlationId("CORR-INTEGRATION-002")
                .build();
        
        // When: Process increase
        assertDoesNotThrow(() -> tradeProcessingService.processTrade(increaseTrade));
        
        // Given: Second trade (decrease) with FIFO
        Contract fifoContract = Contract.builder()
                .contractId("C-INTEGRATION-001")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        contractRulesService.updateContract(fifoContract);
        
        TradeEvent decreaseTrade = TradeEvent.builder()
                .tradeId("T-INTEGRATION-003")
                .positionKey("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6")
                .tradeType("DECREASE")
                .quantity(new BigDecimal("300"))
                .price(new BigDecimal("60.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-INTEGRATION-001")
                .correlationId("CORR-INTEGRATION-003")
                .build();
        
        // When: Process decrease
        assertDoesNotThrow(() -> tradeProcessingService.processTrade(decreaseTrade));
        
        // Then: Verify both trades processed
        assertNotNull(increaseTrade.getSequenceStatus());
        assertNotNull(decreaseTrade.getSequenceStatus());
    }
}
