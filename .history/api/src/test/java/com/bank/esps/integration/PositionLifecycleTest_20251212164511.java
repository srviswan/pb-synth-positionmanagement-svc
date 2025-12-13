package com.bank.esps.integration;

import com.bank.esps.application.service.TradeProcessingService;
import com.bank.esps.domain.enums.EventType;
import com.bank.esps.domain.enums.PositionStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test position lifecycle: create, add, partial close, full close, reopen
 * Verifies UPI changes and position status transitions
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("e2e")
class PositionLifecycleTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:14-alpine"))
            .withDatabaseName("position_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    @Autowired
    private TradeProcessingService tradeProcessingService;
    
    @Autowired
    private EventStoreRepository eventStoreRepository;
    
    @Autowired
    private SnapshotRepository snapshotRepository;
    
    private String positionKey;
    
    @BeforeEach
    void setUp() {
        positionKey = "pos-lifecycle-" + System.currentTimeMillis();
    }
    
    @Test
    void testPositionLifecycle_CloseAndReopen() {
        // Step 1: Create NEW_TRADE (initial position)
        TradeEvent trade1 = TradeEvent.builder()
                .tradeId("T-LIFECYCLE-001")
                .positionKey(positionKey)
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("50.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-LIFECYCLE-001")
                .correlationId("CORR-LIFECYCLE-001")
                .build();
        
        tradeProcessingService.processTrade(trade1);
        
        // Wait for processing - allow more time for snapshot creation
        await().atMost(15, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
            assertTrue(snapshot.isPresent(), "Snapshot should exist after NEW_TRADE");
            SnapshotEntity snap = snapshot.get();
            assertEquals(PositionStatus.ACTIVE, snap.getStatus(), "Position should be ACTIVE");
            assertEquals("T-LIFECYCLE-001", snap.getUti(), "Initial UPI should be first trade ID");
            assertTrue(snap.getLastVer() >= 1L, "Version should be at least 1, got: " + snap.getLastVer());
        });
        
        SnapshotEntity snapshot1 = snapshotRepository.findById(positionKey).orElseThrow();
        String initialUpi = snapshot1.getUti();
        System.out.println("Step 1: NEW_TRADE created - UPI: " + initialUpi + ", Status: " + snapshot1.getStatus());
        
        // Step 2: Add another trade (INCREASE)
        TradeEvent trade2 = TradeEvent.builder()
                .tradeId("T-LIFECYCLE-002")
                .positionKey(positionKey)
                .tradeType("INCREASE")
                .quantity(new BigDecimal("500"))
                .price(new BigDecimal("55.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-LIFECYCLE-001")
                .correlationId("CORR-LIFECYCLE-002")
                .build();
        
        tradeProcessingService.processTrade(trade2);
        
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
            assertTrue(snapshot.isPresent(), "Snapshot should exist after INCREASE");
            assertEquals(PositionStatus.ACTIVE, snapshot.get().getStatus(), "Position should still be ACTIVE");
            assertEquals(initialUpi, snapshot.get().getUti(), "UPI should remain unchanged");
            assertEquals(2L, snapshot.get().getLastVer(), "Version should be 2");
        });
        
        SnapshotEntity snapshot2 = snapshotRepository.findById(positionKey).orElseThrow();
        System.out.println("Step 2: INCREASE processed - UPI: " + snapshot2.getUti() + ", Status: " + snapshot2.getStatus() + 
                          ", Total Qty: " + snapshot2.getSummaryMetrics());
        
        // Step 3: Partial DECREASE (300 shares)
        TradeEvent trade3 = TradeEvent.builder()
                .tradeId("T-LIFECYCLE-003")
                .positionKey(positionKey)
                .tradeType("DECREASE")
                .quantity(new BigDecimal("300"))
                .price(new BigDecimal("60.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-LIFECYCLE-001")
                .correlationId("CORR-LIFECYCLE-003")
                .build();
        
        tradeProcessingService.processTrade(trade3);
        
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
            assertTrue(snapshot.isPresent(), "Snapshot should exist after partial DECREASE");
            assertEquals(PositionStatus.ACTIVE, snapshot.get().getStatus(), "Position should still be ACTIVE (partial close)");
            assertEquals(3L, snapshot.get().getLastVer(), "Version should be 3");
        });
        
        SnapshotEntity snapshot3 = snapshotRepository.findById(positionKey).orElseThrow();
        System.out.println("Step 3: Partial DECREASE (300) - UPI: " + snapshot3.getUti() + ", Status: " + snapshot3.getStatus() + 
                          ", Version: " + snapshot3.getLastVer());
        
        // Step 4: Full DECREASE (remaining 1200 shares: 1000 + 500 - 300 = 1200)
        TradeEvent trade4 = TradeEvent.builder()
                .tradeId("T-LIFECYCLE-004")
                .positionKey(positionKey)
                .tradeType("DECREASE")
                .quantity(new BigDecimal("1200"))
                .price(new BigDecimal("65.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-LIFECYCLE-001")
                .correlationId("CORR-LIFECYCLE-004")
                .build();
        
        tradeProcessingService.processTrade(trade4);
        
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
            assertTrue(snapshot.isPresent(), "Snapshot should exist after full DECREASE");
            assertEquals(PositionStatus.TERMINATED, snapshot.get().getStatus(), 
                    "Position should be TERMINATED when qty = 0");
            assertEquals(4L, snapshot.get().getLastVer(), "Version should be 4");
        });
        
        SnapshotEntity snapshot4 = snapshotRepository.findById(positionKey).orElseThrow();
        System.out.println("Step 4: Full DECREASE (1200) - UPI: " + snapshot4.getUti() + ", Status: " + snapshot4.getStatus() + 
                          ", Version: " + snapshot4.getLastVer());
        
        // Verify position is closed
        assertEquals(PositionStatus.TERMINATED, snapshot4.getStatus(), "Position should be TERMINATED");
        assertEquals(initialUpi, snapshot4.getUti(), "UPI should remain same until reopened");
        
        // Step 5: Reopen position with NEW_TRADE
        TradeEvent trade5 = TradeEvent.builder()
                .tradeId("T-LIFECYCLE-005")
                .positionKey(positionKey)
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("2000"))
                .price(new BigDecimal("70.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-LIFECYCLE-002")
                .correlationId("CORR-LIFECYCLE-005")
                .build();
        
        tradeProcessingService.processTrade(trade5);
        
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
            assertTrue(snapshot.isPresent(), "Snapshot should exist after reopening");
            assertEquals(PositionStatus.ACTIVE, snapshot.get().getStatus(), 
                    "Position should be ACTIVE after reopening");
            assertEquals("T-LIFECYCLE-005", snapshot.get().getUti(), 
                    "UPI should be updated to new trade ID when reopened");
            assertEquals(5L, snapshot.get().getLastVer(), "Version should be 5");
        });
        
        SnapshotEntity snapshot5 = snapshotRepository.findById(positionKey).orElseThrow();
        System.out.println("Step 5: NEW_TRADE (reopen) - UPI: " + snapshot5.getUti() + ", Status: " + snapshot5.getStatus() + 
                          ", Version: " + snapshot5.getLastVer());
        
        // Verify position is reopened with new UPI
        assertEquals(PositionStatus.ACTIVE, snapshot5.getStatus(), "Position should be ACTIVE after reopening");
        assertEquals("T-LIFECYCLE-005", snapshot5.getUti(), "UPI should be new trade ID");
        assertNotEquals(initialUpi, snapshot5.getUti(), "UPI should be different from initial UPI");
        
        // Verify all events were stored
        List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
        assertEquals(5, events.size(), "Should have 5 events");
        assertEquals(EventType.NEW_TRADE, events.get(0).getEventType());
        assertEquals(EventType.INCREASE, events.get(1).getEventType());
        assertEquals(EventType.DECREASE, events.get(2).getEventType());
        assertEquals(EventType.DECREASE, events.get(3).getEventType());
        assertEquals(EventType.NEW_TRADE, events.get(4).getEventType());
        
        System.out.println("\n✅ Position Lifecycle Test Complete!");
        System.out.println("Initial UPI: " + initialUpi);
        System.out.println("Final UPI: " + snapshot5.getUti());
        System.out.println("Status Transition: ACTIVE -> ACTIVE -> ACTIVE -> TERMINATED -> ACTIVE");
    }
}
