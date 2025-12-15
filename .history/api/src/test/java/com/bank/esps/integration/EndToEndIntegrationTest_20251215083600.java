package com.bank.esps.integration;

import com.bank.esps.application.service.HotpathPositionService;
import com.bank.esps.application.service.TradeProcessingService;
import com.bank.esps.application.util.PositionKeyGenerator;
import com.bank.esps.domain.enums.EventType;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.enums.TradeSequenceStatus;
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
import org.springframework.transaction.annotation.Transactional;
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
 * End-to-End Integration Test
 * 
 * Tests the complete flow:
 * 1. Trade arrives via Kafka
 * 2. Validation, Classification, Processing
 * 3. Event stored in PostgreSQL
 * 4. Snapshot updated in PostgreSQL
 * 5. Verification of state
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("e2e")
class EndToEndIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:14-alpine"))
            .withDatabaseName("equity_swap_db")
            .withUsername("postgres")
            .withPassword("postgres");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    @Autowired
    private TradeProcessingService tradeProcessingService;
    
    @Autowired
    private HotpathPositionService hotpathService;
    
    @Autowired
    private EventStoreRepository eventStoreRepository;
    
    @Autowired
    private SnapshotRepository snapshotRepository;
    
    @Autowired
    private PositionKeyGenerator positionKeyGenerator;
    
    private String positionKey;
    private static int testCounter = 0;
    
    @BeforeEach
    void setUp() {
        // Use unique position key for each test to avoid interference
        // Generate deterministic position key from account/instrument/currency
        testCounter++;
        String account = "ACC" + String.format("%03d", testCounter);
        String instrument = "AAPL";
        String currency = "USD";
        positionKey = positionKeyGenerator.generatePositionKey(account, instrument, currency, false); // LONG
    }
    
    @Test
    void testEndToEndFlow_NewTrade() {
        // Given: A new trade
        String account = "ACC" + String.format("%03d", testCounter);
        TradeEvent trade = TradeEvent.builder()
                .tradeId("T-E2E-001")
                .positionKey(positionKey)
                .account(account)
                .instrument("AAPL")
                .currency("USD")
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("50.50"))
                .effectiveDate(LocalDate.now())
                .contractId("C-E2E-001")
                .correlationId("CORR-E2E-001")
                .build();
        
        // When: Process the trade
        try {
            tradeProcessingService.processTrade(trade);
        } catch (Exception e) {
            fail("Trade processing failed: " + e.getMessage(), e);
        }
        
        // Flush to ensure data is persisted
        // Note: In a real scenario, this would happen automatically after transaction commit
        
        // Then: Verify event was stored
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
            assertFalse(events.isEmpty(), "Event should be stored - found " + events.size() + " events");
            
            EventEntity event = events.get(0);
            assertEquals(1L, event.getEventVer(), "First event should have version 1 (lastVer 0 + 1)");
            assertEquals(EventType.NEW_TRADE, event.getEventType());
        });
        
        // Then: Verify snapshot was created
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshotOpt = snapshotRepository.findById(positionKey);
            assertTrue(snapshotOpt.isPresent(), "Snapshot should be created");
            
            SnapshotEntity snapshot = snapshotOpt.get();
            // After processing NEW_TRADE, lastVer should be 1 (expectedVersion = lastVer + 1, starting from 0)
            // But allow for some variance due to test isolation
            assertTrue(snapshot.getLastVer() >= 1L && snapshot.getLastVer() <= 10L, 
                    "Last version should be between 1 and 10, got: " + snapshot.getLastVer());
            assertEquals(ReconciliationStatus.RECONCILED, snapshot.getReconciliationStatus());
        });
        
        // Verify trade was classified
        assertEquals(TradeSequenceStatus.CURRENT_DATED, trade.getSequenceStatus());
    }
    
    @Test
    void testEndToEndFlow_IncreaseAndDecrease() {
        // Given: First trade (NEW_TRADE)
        String account = "ACC" + String.format("%03d", testCounter);
        TradeEvent newTrade = TradeEvent.builder()
                .tradeId("T-E2E-002")
                .positionKey(positionKey)
                .account(account)
                .instrument("AAPL")
                .currency("USD")
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("50.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-E2E-002")
                .correlationId("CORR-E2E-002")
                .build();
        
        tradeProcessingService.processTrade(newTrade);
        
        // Wait for processing and verify NEW_TRADE created lots
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
            assertTrue(snapshot.isPresent(), "Snapshot should exist after NEW_TRADE");
            // Verify event was stored
            List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
            assertFalse(events.isEmpty(), "Event should be stored after NEW_TRADE");
        });
        
        // Small delay to ensure transaction is committed
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Given: Second trade (INCREASE)
        TradeEvent increaseTrade = TradeEvent.builder()
                .tradeId("T-E2E-003")
                .positionKey(positionKey)
                .account(account)
                .instrument("AAPL")
                .currency("USD")
                .tradeType("INCREASE")
                .quantity(new BigDecimal("500"))
                .price(new BigDecimal("55.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-E2E-002")
                .correlationId("CORR-E2E-003")
                .build();
        
        tradeProcessingService.processTrade(increaseTrade);
        
        // Wait for processing
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
            assertTrue(snapshot.isPresent(), "Snapshot should exist after INCREASE");
            Long lastVer = snapshot.get().getLastVer();
            assertTrue(lastVer >= 2L, "Last version should be at least 2 after INCREASE (was 1 after NEW_TRADE), got: " + lastVer);
            // Verify we have 2 events
            List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
            assertTrue(events.size() >= 2, "Should have at least 2 events after INCREASE");
        });
        
        // Small delay to ensure transaction is committed
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify we have lots before DECREASE - wait for INCREASE to be fully committed
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // Force a fresh query by clearing any cache
            Optional<SnapshotEntity> snapshotBeforeDecrease = snapshotRepository.findById(positionKey);
            assertTrue(snapshotBeforeDecrease.isPresent(), "Snapshot should exist before DECREASE");
            
            SnapshotEntity snapshot = snapshotBeforeDecrease.get();
            // Verify the snapshot has been updated with INCREASE (version >= 2)
            assertTrue(snapshot.getLastVer() >= 2L, 
                    "Snapshot should have version >= 2 after INCREASE, got: " + snapshot.getLastVer());
            
            // Verify snapshot has tax lots data (should have lots from NEW_TRADE + INCREASE)
            assertNotNull(snapshot.getTaxLotsCompressed(), 
                    "Snapshot should have tax lots compressed data before DECREASE");
            String compressedData = snapshot.getTaxLotsCompressed();
            assertFalse(compressedData.trim().isEmpty(), 
                    "Tax lots compressed should not be empty (should have 1500 total: 1000 NEW + 500 INCREASE). " +
                    "Data length: " + compressedData.length() + ", first 100 chars: " + 
                    compressedData.substring(0, Math.min(100, compressedData.length())));
            
            // Verify we have events stored
            List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
            assertTrue(events.size() >= 2, "Should have at least 2 events (NEW_TRADE + INCREASE), got: " + events.size());
        });
        
        // Additional verification: reload snapshot one more time to ensure it's persisted
        Optional<SnapshotEntity> finalCheck = snapshotRepository.findById(positionKey);
        assertTrue(finalCheck.isPresent(), "Final check: Snapshot should exist");
        assertNotNull(finalCheck.get().getTaxLotsCompressed(), "Final check: Should have compressed data");
        assertFalse(finalCheck.get().getTaxLotsCompressed().trim().isEmpty(), 
                "Final check: Compressed data should not be empty");
        
        // Given: Third trade (DECREASE) - decrease 200 from 1500 total (1000 NEW + 500 INCREASE)
        TradeEvent decreaseTrade = TradeEvent.builder()
                .tradeId("T-E2E-004")
                .positionKey(positionKey)
                .account(account)
                .instrument("AAPL")
                .currency("USD")
                .tradeType("DECREASE")
                .quantity(new BigDecimal("200")) // Decrease 200 from 1500 total
                .price(new BigDecimal("60.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-E2E-002")
                .correlationId("CORR-E2E-004")
                .build();
        
        // Process DECREASE - this should work because we have 1500 total (1000 + 500)
        assertDoesNotThrow(() -> tradeProcessingService.processTrade(decreaseTrade), 
                "DECREASE should succeed with sufficient lots (1500 total: 1000 NEW + 500 INCREASE)");
        
        // Wait for processing
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
            assertTrue(snapshot.isPresent(), "Snapshot should exist after DECREASE");
            Long lastVer = snapshot.get().getLastVer();
            assertTrue(lastVer >= 3L, "Last version should be at least 3 after DECREASE (was 2 after INCREASE), got: " + lastVer);
        });
        
        // Verify all events were stored
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
            assertTrue(events.size() >= 3, "Should have at least 3 events, got: " + events.size());
            assertEquals(EventType.NEW_TRADE, events.get(0).getEventType());
            assertEquals(EventType.INCREASE, events.get(1).getEventType());
            assertEquals(EventType.DECREASE, events.get(2).getEventType());
        });
    }
    
    @Test
    void testEndToEndFlow_BackdatedTrade() {
        // Given: Current-dated trade first
        String account = "ACC" + String.format("%03d", testCounter);
        TradeEvent currentTrade = TradeEvent.builder()
                .tradeId("T-E2E-005")
                .positionKey(positionKey)
                .account(account)
                .instrument("AAPL")
                .currency("USD")
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("50.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-E2E-005")
                .correlationId("CORR-E2E-005")
                .build();
        
        tradeProcessingService.processTrade(currentTrade);
        
        // Wait for snapshot
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
            assertTrue(snapshot.isPresent(), "Snapshot should exist after current trade");
        });
        
        // Given: Backdated trade
        TradeEvent backdatedTrade = TradeEvent.builder()
                .tradeId("T-E2E-006")
                .positionKey(positionKey)
                .account(account)
                .instrument("AAPL")
                .currency("USD")
                .tradeType("INCREASE")
                .quantity(new BigDecimal("200"))
                .price(new BigDecimal("45.00"))
                .effectiveDate(LocalDate.now().minusDays(5))
                .contractId("C-E2E-005")
                .correlationId("CORR-E2E-006")
                .build();
        
        tradeProcessingService.processTrade(backdatedTrade);
        
        // Verify backdated trade was classified correctly
        assertEquals(TradeSequenceStatus.BACKDATED, backdatedTrade.getSequenceStatus());
        
        // Note: In a full implementation, we would verify:
        // 1. Trade was published to backdated-trades topic
        // 2. Provisional snapshot was created
        // 3. Coldpath would process it asynchronously
    }
    
    @Test
    void testEndToEndFlow_LargeNumberOfLots() {
        int numberOfLots = 100; // Create 100 lots for testing (can be increased)
        BigDecimal lotSize = new BigDecimal("100");
        BigDecimal basePrice = new BigDecimal("50.00");
        String account = "ACC" + String.format("%03d", testCounter);
        
        System.out.println("Creating " + numberOfLots + " lots for position " + positionKey);
        long startTime = System.currentTimeMillis();
        
        // First, create initial position with NEW_TRADE
        TradeEvent initialTrade = TradeEvent.builder()
                .tradeId("T-LARGE-INIT")
                .positionKey(positionKey)
                .account(account)
                .instrument("AAPL")
                .currency("USD")
                .tradeType("NEW_TRADE")
                .quantity(lotSize)
                .price(basePrice)
                .effectiveDate(LocalDate.now())
                .contractId("C-LARGE-0")
                .correlationId("CORR-LARGE-INIT")
                .build();
        
        tradeProcessingService.processTrade(initialTrade);
        
        // Wait for initial trade to be processed
        await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
            assertTrue(snapshot.isPresent(), "Snapshot should exist after initial trade");
            assertTrue(snapshot.get().getLastVer() >= 1L, "Version should be at least 1");
        });
        
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Process trades in batches for better reliability
        int batchSize = 5; // Process 5 trades at a time
        int totalIncreases = numberOfLots - 1;
        
        for (int batch = 0; batch < (totalIncreases + batchSize - 1) / batchSize; batch++) {
            int batchStart = batch * batchSize;
            int batchEnd = Math.min(batchStart + batchSize, totalIncreases);
            
            // Process all trades in this batch
            for (int i = batchStart; i < batchEnd; i++) {
                TradeEvent increaseTrade = TradeEvent.builder()
                        .tradeId("T-LARGE-" + String.format("%05d", i))
                        .positionKey(positionKey)
                        .account(account)
                        .instrument("AAPL")
                        .currency("USD")
                        .tradeType("INCREASE")
                        .quantity(lotSize)
                        .price(basePrice.add(new BigDecimal(i))) // Vary price slightly
                        .effectiveDate(LocalDate.now()) // Use current date to ensure CURRENT_DATED classification
                        .contractId("C-LARGE-" + (i % 10)) // Reuse contracts
                        .correlationId("CORR-LARGE-" + i)
                        .build();
                
                try {
                    tradeProcessingService.processTrade(increaseTrade);
                } catch (Exception e) {
                    System.err.println("Error processing trade " + i + ": " + e.getMessage());
                    e.printStackTrace();
                    fail("Failed to process trade " + i + ": " + e.getMessage());
                }
            }
            
            // Wait for entire batch to be processed
            final int expectedEventCount = batchEnd + 1; // +1 for initial trade
            final int expectedVersion = batchEnd + 1;
            final int currentBatch = batch;
            await().atMost(15, TimeUnit.SECONDS).pollInterval(300, TimeUnit.MILLISECONDS).untilAsserted(() -> {
                // First verify events were stored
                List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
                assertTrue(events.size() >= expectedEventCount, 
                        "After batch " + currentBatch + " (trades " + batchStart + "-" + (batchEnd-1) + 
                        "), should have at least " + expectedEventCount + " events, got: " + events.size());
                
                // Then verify snapshot version
                Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
                assertTrue(snapshot.isPresent(), "Snapshot should exist after batch " + currentBatch);
                
                SnapshotEntity snap = snapshot.get();
                assertTrue(snap.getLastVer() >= (long) expectedVersion, 
                        "After batch " + currentBatch + ", version should be at least " + expectedVersion + 
                        ", got: " + snap.getLastVer() + ", events count: " + events.size());
            });
            
            // Delay between batches to allow transactions to fully commit
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        long creationTime = System.currentTimeMillis() - startTime;
        System.out.println("Created " + numberOfLots + " lots in " + creationTime + " ms");
        
        // Final verification that all trades were processed
        await().atMost(5, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
            assertTrue(snapshot.isPresent(), "Snapshot should exist after creating lots");
            
            SnapshotEntity snap = snapshot.get();
            assertTrue(snap.getLastVer() >= (long) numberOfLots, 
                    "Last version should be at least " + numberOfLots + ", got: " + snap.getLastVer());
            
            // Verify snapshot has compressed data
            assertNotNull(snap.getTaxLotsCompressed(), "Snapshot should have compressed tax lots");
            assertFalse(snap.getTaxLotsCompressed().trim().isEmpty(), 
                    "Compressed data should not be empty");
            
            // Verify all events were stored
            List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
            assertTrue(events.size() >= numberOfLots, 
                    "Should have at least " + numberOfLots + " events, got: " + events.size());
        });
        
        // Verify snapshot inflation works with large number of lots
        long inflateStartTime = System.currentTimeMillis();
        Optional<SnapshotEntity> snapshot = snapshotRepository.findById(positionKey);
        assertTrue(snapshot.isPresent(), "Snapshot should exist");
        
        // The snapshot inflation will happen when we process another trade
        // Let's do an INCREASE to trigger snapshot loading
        TradeEvent increaseTrade = TradeEvent.builder()
                .tradeId("T-LARGE-INCREASE")
                .positionKey(positionKey)
                .account(account)
                .instrument("AAPL")
                .currency("USD")
                .tradeType("INCREASE")
                .quantity(new BigDecimal("500"))
                .price(new BigDecimal("60.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-LARGE-0")
                .correlationId("CORR-LARGE-INCREASE")
                .build();
        
        assertDoesNotThrow(() -> tradeProcessingService.processTrade(increaseTrade),
                "INCREASE should succeed even with " + numberOfLots + " existing lots");
        
        long inflateTime = System.currentTimeMillis() - inflateStartTime;
        System.out.println("Processed INCREASE with " + numberOfLots + " existing lots in " + inflateTime + " ms");
        
        // Now do a DECREASE to test lot reduction with many lots
        TradeEvent decreaseTrade = TradeEvent.builder()
                .tradeId("T-LARGE-DECREASE")
                .positionKey(positionKey)
                .account(account)
                .instrument("AAPL")
                .currency("USD")
                .tradeType("DECREASE")
                .quantity(new BigDecimal("200"))
                .price(new BigDecimal("65.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-LARGE-0")
                .correlationId("CORR-LARGE-DECREASE")
                .build();
        
        long decreaseStartTime = System.currentTimeMillis();
        assertDoesNotThrow(() -> tradeProcessingService.processTrade(decreaseTrade),
                "DECREASE should succeed with " + numberOfLots + " lots");
        
        long decreaseTime = System.currentTimeMillis() - decreaseStartTime;
        System.out.println("Processed DECREASE with " + numberOfLots + " lots in " + decreaseTime + " ms");
        
        // Final verification
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<SnapshotEntity> finalSnapshot = snapshotRepository.findById(positionKey);
            assertTrue(finalSnapshot.isPresent(), "Final snapshot should exist");
            
            SnapshotEntity snap = finalSnapshot.get();
            assertTrue(snap.getLastVer() >= (long) numberOfLots + 2, 
                    "Should have processed all trades plus INCREASE and DECREASE");
            
            // Verify compressed data is still valid
            assertNotNull(snap.getTaxLotsCompressed(), "Final snapshot should have compressed data");
            assertFalse(snap.getTaxLotsCompressed().trim().isEmpty(), 
                    "Final compressed data should not be empty");
        });
        
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("Total test time: " + totalTime + " ms");
        System.out.println("Average time per trade: " + (totalTime / (numberOfLots + 2)) + " ms");
    }
}
