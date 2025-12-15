package com.bank.esps.application.performance;

import com.bank.esps.application.service.*;
import com.bank.esps.domain.enums.PositionStatus;
import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.domain.enums.TradeSequenceStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.Contract;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for Position Management Service
 * Tests various scenarios: hotpath, coldpath, batch processing
 */
@SpringBootTest
@ActiveProfiles("test")
class PositionServicePerformanceTest {
    
    private static final Logger log = LoggerFactory.getLogger(PositionServicePerformanceTest.class);
    
    @Autowired
    private HotpathPositionService hotpathPositionService;
    
    @Autowired
    private RecalculationService recalculationService;
    
    @Autowired
    private ContractRulesService contractRulesService;
    
    @Autowired
    private SnapshotRepository snapshotRepository;
    
    @Autowired
    private EventStoreRepository eventStoreRepository;
    
    @Autowired
    private SnapshotService snapshotService;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.bank.esps.domain.messaging.MessageProducer messageProducer;
    
    @Autowired(required = false)
    private MetricsService metricsService;
    
    @Autowired
    private com.bank.esps.application.util.PositionKeyGenerator positionKeyGenerator;
    
    private String testPositionKey;
    private String testAccount = "PERF-ACC-001";
    private String testInstrument = "AAPL";
    private String testCurrency = "USD";
    
    @BeforeEach
    void setUp() {
        // Generate deterministic position key for performance testing
        testPositionKey = positionKeyGenerator.generatePositionKey(testAccount, testInstrument, testCurrency, false);
        log.info("Starting performance test with position key: {}", testPositionKey);
    }
    
    /**
     * Test 1: Hotpath Performance - Sequential Current-Dated Trades
     * Measures latency for typical hotpath processing
     */
    @Test
    void testHotpathPerformance_SequentialTrades() {
        log.info("=== Test 1: Hotpath Performance - Sequential Current-Dated Trades ===");
        
        int tradeCount = 100;
        List<Long> latencies = new ArrayList<>();
        
        // Setup contract
        Contract contract = Contract.builder()
                .contractId("PERF-CONTRACT-001")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        contractRulesService.updateContract(contract);
        
        LocalDate baseDate = LocalDate.now();
        
        for (int i = 0; i < tradeCount; i++) {
            long startTime = System.nanoTime();
            
            TradeEvent trade = TradeEvent.builder()
                    .tradeId("HOTPATH-T-" + i + "-" + System.currentTimeMillis())
                    .positionKey(testPositionKey)
                    .account(testAccount)
                    .instrument(testInstrument)
                    .currency(testCurrency)
                    .tradeType(i == 0 ? "NEW_TRADE" : (i % 2 == 0 ? "INCREASE" : "DECREASE"))
                    .quantity(new BigDecimal(i % 2 == 0 ? "100" : "50"))
                    .price(new BigDecimal("50.00").add(new BigDecimal(i * 0.1)))
                    .effectiveDate(baseDate.plusDays(i))
                    .contractId("PERF-CONTRACT-001")
                    .correlationId("CORR-PERF-001")
                    .causationId("CAUS-PERF-001")
                    .userId("PERF-USER")
                    .build();
            
            hotpathPositionService.processCurrentDatedTrade(trade);
            
            long endTime = System.nanoTime();
            long latencyMs = (endTime - startTime) / 1_000_000;
            latencies.add(latencyMs);
            
            if (i % 10 == 0) {
                log.info("Processed trade {}: {}ms", i, latencyMs);
            }
        }
        
        // Calculate statistics
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long p50 = latencies.stream().sorted().skip(latencies.size() / 2).findFirst().orElse(0L);
        long p95 = latencies.stream().sorted().skip((long)(latencies.size() * 0.95)).findFirst().orElse(0L);
        long p99 = latencies.stream().sorted().skip((long)(latencies.size() * 0.99)).findFirst().orElse(0L);
        long max = latencies.stream().mapToLong(Long::longValue).max().orElse(0L);
        
        log.info("=== Hotpath Performance Results ===");
        log.info("Total trades: {}", tradeCount);
        log.info("Average latency: {:.2f}ms", avgLatency);
        log.info("P50 latency: {}ms", p50);
        log.info("P95 latency: {}ms", p95);
        log.info("P99 latency: {}ms", p99);
        log.info("Max latency: {}ms", max);
        
        // Assertions
        assertTrue(avgLatency < 100, "Average latency should be < 100ms");
        assertTrue(p99 < 200, "P99 latency should be < 200ms");
    }
    
    /**
     * Test 2: Coldpath Performance - Backdated Trade Recalculation
     * Measures latency for backdated trade processing with event replay
     */
    @Test
    void testColdpathPerformance_BackdatedTrade() {
        log.info("=== Test 2: Coldpath Performance - Backdated Trade Recalculation ===");
        
        // First, create a position with some trades
        Contract contract = Contract.builder()
                .contractId("PERF-CONTRACT-002")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        contractRulesService.updateContract(contract);
        
        LocalDate baseDate = LocalDate.now().minusDays(10);
        
        // Create 20 forward-dated trades
        for (int i = 0; i < 20; i++) {
            TradeEvent trade = TradeEvent.builder()
                    .tradeId("FORWARD-T-" + i)
                    .positionKey(testPositionKey)
                    .tradeType(i == 0 ? "NEW_TRADE" : "INCREASE")
                    .quantity(new BigDecimal("100"))
                    .price(new BigDecimal("50.00").add(new BigDecimal(i * 0.5)))
                    .effectiveDate(baseDate.plusDays(i))
                    .contractId("PERF-CONTRACT-002")
                    .correlationId("CORR-PERF-002")
                    .causationId("CAUS-PERF-002")
                    .userId("PERF-USER")
                    .build();
            
            hotpathPositionService.processCurrentDatedTrade(trade);
        }
        
        log.info("Created position with 20 trades, now testing backdated trade recalculation");
        
        // Now test backdated trade recalculation
        int backdatedTradeCount = 5;
        List<Long> latencies = new ArrayList<>();
        
        for (int i = 0; i < backdatedTradeCount; i++) {
            long startTime = System.nanoTime();
            
            TradeEvent backdatedTrade = TradeEvent.builder()
                    .tradeId("BACKDATED-T-" + i)
                    .positionKey(testPositionKey)
                    .tradeType("INCREASE")
                    .quantity(new BigDecimal("50"))
                    .price(new BigDecimal("45.00"))
                    .effectiveDate(baseDate.plusDays(i * 2)) // Backdated to earlier dates
                    .sequenceStatus(TradeSequenceStatus.BACKDATED)
                    .contractId("PERF-CONTRACT-002")
                    .correlationId("CORR-PERF-002")
                    .causationId("CAUS-PERF-002")
                    .userId("PERF-USER")
                    .build();
            
            recalculationService.recalculatePosition(backdatedTrade);
            
            long endTime = System.nanoTime();
            long latencyMs = (endTime - startTime) / 1_000_000;
            latencies.add(latencyMs);
            
            log.info("Processed backdated trade {}: {}ms", i, latencyMs);
        }
        
        // Calculate statistics
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long p50 = latencies.stream().sorted().skip(latencies.size() / 2).findFirst().orElse(0L);
        long p95 = latencies.stream().sorted().skip((long)(latencies.size() * 0.95)).findFirst().orElse(0L);
        long max = latencies.stream().mapToLong(Long::longValue).max().orElse(0L);
        
        log.info("=== Coldpath Performance Results ===");
        log.info("Total backdated trades: {}", backdatedTradeCount);
        log.info("Average latency: {:.2f}ms", avgLatency);
        log.info("P50 latency: {}ms", p50);
        log.info("P95 latency: {}ms", p95);
        log.info("Max latency: {}ms", max);
        
        // Coldpath is expected to be slower (event replay)
        assertTrue(avgLatency < 1000, "Average coldpath latency should be < 1000ms");
    }
    
    /**
     * Test 3: Batch Processing Performance
     * Tests processing multiple trades in parallel
     */
    @Test
    void testBatchProcessingPerformance() throws Exception {
        log.info("=== Test 3: Batch Processing Performance ===");
        
        Contract contract = Contract.builder()
                .contractId("PERF-CONTRACT-003")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        contractRulesService.updateContract(contract);
        
        int batchSize = 50;
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        LocalDate baseDate = LocalDate.now();
        
        long startTime = System.nanoTime();
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int batch = 0; batch < batchSize; batch++) {
            final int batchNum = batch;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    TradeEvent trade = TradeEvent.builder()
                            .tradeId("BATCH-T-" + batchNum + "-" + Thread.currentThread().getId())
                            .positionKey(testPositionKey + "-BATCH-" + batchNum)
                            .tradeType("NEW_TRADE")
                            .quantity(new BigDecimal("100"))
                            .price(new BigDecimal("50.00"))
                            .effectiveDate(baseDate.plusDays(batchNum))
                            .contractId("PERF-CONTRACT-003")
                            .correlationId("CORR-BATCH-" + batchNum)
                            .causationId("CAUS-BATCH-" + batchNum)
                            .userId("PERF-USER")
                            .build();
                    
                    hotpathPositionService.processCurrentDatedTrade(trade);
                } catch (Exception e) {
                    log.error("Error processing batch trade {}", batchNum, e);
                }
            }, executor);
            
            futures.add(future);
        }
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        
        long endTime = System.nanoTime();
        long totalTimeMs = (endTime - startTime) / 1_000_000;
        double throughput = (double) batchSize / (totalTimeMs / 1000.0);
        
        executor.shutdown();
        
        log.info("=== Batch Processing Performance Results ===");
        log.info("Batch size: {}", batchSize);
        log.info("Thread count: {}", threadCount);
        log.info("Total time: {}ms", totalTimeMs);
        log.info("Throughput: {:.2f} trades/second", throughput);
        log.info("Average latency per trade: {:.2f}ms", (double) totalTimeMs / batchSize);
        
        assertTrue(throughput > 10, "Throughput should be > 10 trades/second");
    }
    
    /**
     * Test 4: Mixed Workload Performance
     * Tests mix of hotpath and coldpath trades
     */
    @Test
    void testMixedWorkloadPerformance() {
        log.info("=== Test 4: Mixed Workload Performance ===");
        
        Contract contract = Contract.builder()
                .contractId("PERF-CONTRACT-004")
                .taxLotMethod(TaxLotMethod.LIFO)
                .build();
        contractRulesService.updateContract(contract);
        
        LocalDate baseDate = LocalDate.now().minusDays(5);
        int hotpathTrades = 30;
        int coldpathTrades = 5;
        
        List<Long> hotpathLatencies = new ArrayList<>();
        List<Long> coldpathLatencies = new ArrayList<>();
        
        // Process hotpath trades first
        for (int i = 0; i < hotpathTrades; i++) {
            long startTime = System.nanoTime();
            
            TradeEvent trade = TradeEvent.builder()
                    .tradeId("MIXED-HOT-" + i)
                    .positionKey(testPositionKey)
                    .tradeType(i == 0 ? "NEW_TRADE" : "INCREASE")
                    .quantity(new BigDecimal("100"))
                    .price(new BigDecimal("50.00"))
                    .effectiveDate(baseDate.plusDays(i))
                    .contractId("PERF-CONTRACT-004")
                    .correlationId("CORR-MIXED")
                    .causationId("CAUS-MIXED")
                    .userId("PERF-USER")
                    .build();
            
            hotpathPositionService.processCurrentDatedTrade(trade);
            
            long endTime = System.nanoTime();
            hotpathLatencies.add((endTime - startTime) / 1_000_000);
        }
        
        log.info("Processed {} hotpath trades, now processing {} backdated trades", 
                hotpathTrades, coldpathTrades);
        
        // Process coldpath trades
        for (int i = 0; i < coldpathTrades; i++) {
            long startTime = System.nanoTime();
            
            TradeEvent backdatedTrade = TradeEvent.builder()
                    .tradeId("MIXED-COLD-" + i)
                    .positionKey(testPositionKey)
                    .tradeType("INCREASE")
                    .quantity(new BigDecimal("50"))
                    .price(new BigDecimal("45.00"))
                    .effectiveDate(baseDate.plusDays(i)) // Backdated
                    .sequenceStatus(TradeSequenceStatus.BACKDATED)
                    .contractId("PERF-CONTRACT-004")
                    .correlationId("CORR-MIXED")
                    .causationId("CAUS-MIXED")
                    .userId("PERF-USER")
                    .build();
            
            recalculationService.recalculatePosition(backdatedTrade);
            
            long endTime = System.nanoTime();
            coldpathLatencies.add((endTime - startTime) / 1_000_000);
        }
        
        // Calculate statistics
        double avgHotpath = hotpathLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
        double avgColdpath = coldpathLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long p99Hotpath = hotpathLatencies.stream().sorted().skip((long)(hotpathLatencies.size() * 0.99)).findFirst().orElse(0L);
        long p99Coldpath = coldpathLatencies.stream().sorted().skip((long)(coldpathLatencies.size() * 0.99)).findFirst().orElse(0L);
        
        log.info("=== Mixed Workload Performance Results ===");
        log.info("Hotpath trades: {}", hotpathTrades);
        log.info("  Average latency: {:.2f}ms", avgHotpath);
        log.info("  P99 latency: {}ms", p99Hotpath);
        log.info("Coldpath trades: {}", coldpathTrades);
        log.info("  Average latency: {:.2f}ms", avgColdpath);
        log.info("  P99 latency: {}ms", p99Coldpath);
        
        assertTrue(avgHotpath < 100, "Hotpath average should be < 100ms");
        assertTrue(p99Hotpath < 200, "Hotpath P99 should be < 200ms");
    }
    
    /**
     * Test 5: Large Position Performance
     * Tests performance with many lots (stress test)
     */
    @Test
    void testLargePositionPerformance() {
        log.info("=== Test 5: Large Position Performance (Many Lots) ===");
        
        Contract contract = Contract.builder()
                .contractId("PERF-CONTRACT-005")
                .taxLotMethod(com.bank.esps.domain.enums.TaxLotMethod.FIFO)
                .build();
        contractRulesService.updateContract(contract);
        
        int lotCount = 500;
        LocalDate baseDate = LocalDate.now();
        
        long startTime = System.nanoTime();
        
        // Create many lots
        for (int i = 0; i < lotCount; i++) {
            TradeEvent trade = TradeEvent.builder()
                    .tradeId("LARGE-T-" + i)
                    .positionKey(testPositionKey)
                    .tradeType(i == 0 ? "NEW_TRADE" : "INCREASE")
                    .quantity(new BigDecimal("10"))
                    .price(new BigDecimal("50.00").add(new BigDecimal(i * 0.01)))
                    .effectiveDate(baseDate.plusDays(i))
                    .contractId("PERF-CONTRACT-005")
                    .correlationId("CORR-LARGE")
                    .causationId("CAUS-LARGE")
                    .userId("PERF-USER")
                    .build();
            
            hotpathPositionService.processCurrentDatedTrade(trade);
            
            if (i % 100 == 0) {
                log.info("Created {} lots", i);
            }
        }
        
        long endTime = System.nanoTime();
        long totalTimeMs = (endTime - startTime) / 1_000_000;
        double avgLatency = (double) totalTimeMs / lotCount;
        
        // Verify snapshot
        SnapshotEntity snapshot = snapshotRepository.findById(testPositionKey).orElse(null);
        assertNotNull(snapshot, "Snapshot should exist");
        
        PositionState state = snapshotService.inflateSnapshot(snapshot);
        assertEquals(lotCount, state.getAllLots().size(), "Should have all lots");
        
        log.info("=== Large Position Performance Results ===");
        log.info("Total lots created: {}", lotCount);
        log.info("Total time: {}ms", totalTimeMs);
        log.info("Average latency per lot: {:.2f}ms", avgLatency);
        log.info("Final position quantity: {}", state.getTotalQty());
        log.info("Final lot count: {}", state.getAllLots().size());
        
        assertTrue(avgLatency < 50, "Average latency should be < 50ms even with many lots");
    }
    
    /**
     * Test 6: Position Lifecycle Performance
     * Tests create -> add -> partial close -> full close -> reopen cycle
     */
    @Test
    void testPositionLifecyclePerformance() {
        log.info("=== Test 6: Position Lifecycle Performance ===");
        
        Contract contract = Contract.builder()
                .contractId("PERF-CONTRACT-006")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        contractRulesService.updateContract(contract);
        
        LocalDate baseDate = LocalDate.now();
        List<Long> latencies = new ArrayList<>();
        
        // 1. Create position
        long startTime = System.nanoTime();
        TradeEvent newTrade = TradeEvent.builder()
                .tradeId("LIFECYCLE-NEW")
                .positionKey(testPositionKey)
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("50.00"))
                .effectiveDate(baseDate)
                .contractId("PERF-CONTRACT-006")
                .correlationId("CORR-LIFECYCLE")
                .causationId("CAUS-LIFECYCLE")
                .userId("PERF-USER")
                .build();
        hotpathPositionService.processCurrentDatedTrade(newTrade);
        latencies.add((System.nanoTime() - startTime) / 1_000_000);
        
        // 2. Add to position
        startTime = System.nanoTime();
        TradeEvent increase = TradeEvent.builder()
                .tradeId("LIFECYCLE-INCREASE")
                .positionKey(testPositionKey)
                .tradeType("INCREASE")
                .quantity(new BigDecimal("500"))
                .price(new BigDecimal("55.00"))
                .effectiveDate(baseDate.plusDays(1))
                .contractId("PERF-CONTRACT-006")
                .correlationId("CORR-LIFECYCLE")
                .causationId("CAUS-LIFECYCLE")
                .userId("PERF-USER")
                .build();
        hotpathPositionService.processCurrentDatedTrade(increase);
        latencies.add((System.nanoTime() - startTime) / 1_000_000);
        
        // 3. Partial close
        startTime = System.nanoTime();
        TradeEvent partialClose = TradeEvent.builder()
                .tradeId("LIFECYCLE-PARTIAL")
                .positionKey(testPositionKey)
                .tradeType("DECREASE")
                .quantity(new BigDecimal("500"))
                .price(new BigDecimal("60.00"))
                .effectiveDate(baseDate.plusDays(2))
                .contractId("PERF-CONTRACT-006")
                .correlationId("CORR-LIFECYCLE")
                .causationId("CAUS-LIFECYCLE")
                .userId("PERF-USER")
                .build();
        hotpathPositionService.processCurrentDatedTrade(partialClose);
        latencies.add((System.nanoTime() - startTime) / 1_000_000);
        
        // 4. Full close
        startTime = System.nanoTime();
        TradeEvent fullClose = TradeEvent.builder()
                .tradeId("LIFECYCLE-FULL")
                .positionKey(testPositionKey)
                .tradeType("DECREASE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("65.00"))
                .effectiveDate(baseDate.plusDays(3))
                .contractId("PERF-CONTRACT-006")
                .correlationId("CORR-LIFECYCLE")
                .causationId("CAUS-LIFECYCLE")
                .userId("PERF-USER")
                .build();
        hotpathPositionService.processCurrentDatedTrade(fullClose);
        latencies.add((System.nanoTime() - startTime) / 1_000_000);
        
        // Verify position is closed
        SnapshotEntity snapshot = snapshotRepository.findById(testPositionKey).orElse(null);
        assertNotNull(snapshot);
        assertEquals(PositionStatus.TERMINATED, snapshot.getStatus(), "Position should be TERMINATED");
        
        // 5. Reopen
        startTime = System.nanoTime();
        TradeEvent reopen = TradeEvent.builder()
                .tradeId("LIFECYCLE-REOPEN")
                .positionKey(testPositionKey)
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("200"))
                .price(new BigDecimal("70.00"))
                .effectiveDate(baseDate.plusDays(4))
                .contractId("PERF-CONTRACT-006")
                .correlationId("CORR-LIFECYCLE")
                .causationId("CAUS-LIFECYCLE")
                .userId("PERF-USER")
                .build();
        hotpathPositionService.processCurrentDatedTrade(reopen);
        latencies.add((System.nanoTime() - startTime) / 1_000_000);
        
        // Verify position is reopened
        snapshot = snapshotRepository.findById(testPositionKey).orElse(null);
        assertNotNull(snapshot);
        assertEquals(PositionStatus.ACTIVE, snapshot.getStatus(), "Position should be ACTIVE after reopen");
        
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxLatency = latencies.stream().mapToLong(Long::longValue).max().orElse(0L);
        
        log.info("=== Position Lifecycle Performance Results ===");
        log.info("Operations: Create, Add, Partial Close, Full Close, Reopen");
        log.info("Average latency: {:.2f}ms", avgLatency);
        log.info("Max latency: {}ms", maxLatency);
        log.info("Individual latencies: {}", latencies);
        
        assertTrue(avgLatency < 100, "Average lifecycle latency should be < 100ms");
    }
    
    /**
     * Test 7: Concurrent Hotpath Performance
     * Tests concurrent processing of multiple positions
     */
    @Test
    void testConcurrentHotpathPerformance() throws Exception {
        log.info("=== Test 7: Concurrent Hotpath Performance ===");
        
        Contract contract = Contract.builder()
                .contractId("PERF-CONTRACT-007")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        contractRulesService.updateContract(contract);
        
        int positionCount = 20;
        int tradesPerPosition = 10;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        LocalDate baseDate = LocalDate.now();
        
        long startTime = System.nanoTime();
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int pos = 0; pos < positionCount; pos++) {
            final int positionNum = pos;
            final String positionKey = testPositionKey + "-CONC-" + positionNum;
            
            for (int trade = 0; trade < tradesPerPosition; trade++) {
                final int tradeNum = trade;
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        TradeEvent tradeEvent = TradeEvent.builder()
                                .tradeId("CONC-T-" + positionNum + "-" + tradeNum)
                                .positionKey(positionKey)
                                .tradeType(tradeNum == 0 ? "NEW_TRADE" : "INCREASE")
                                .quantity(new BigDecimal("100"))
                                .price(new BigDecimal("50.00"))
                                .effectiveDate(baseDate.plusDays(tradeNum))
                                .contractId("PERF-CONTRACT-007")
                                .correlationId("CORR-CONC-" + positionNum)
                                .causationId("CAUS-CONC-" + positionNum)
                                .userId("PERF-USER")
                                .build();
                        
                        hotpathPositionService.processCurrentDatedTrade(tradeEvent);
                    } catch (Exception e) {
                        log.error("Error processing concurrent trade {}-{}", positionNum, tradeNum, e);
                    }
                }, executor);
                
                futures.add(future);
            }
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(120, TimeUnit.SECONDS);
        
        long endTime = System.nanoTime();
        long totalTimeMs = (endTime - startTime) / 1_000_000;
        int totalTrades = positionCount * tradesPerPosition;
        double throughput = (double) totalTrades / (totalTimeMs / 1000.0);
        double avgLatency = (double) totalTimeMs / totalTrades;
        
        executor.shutdown();
        
        log.info("=== Concurrent Hotpath Performance Results ===");
        log.info("Positions: {}", positionCount);
        log.info("Trades per position: {}", tradesPerPosition);
        log.info("Total trades: {}", totalTrades);
        log.info("Total time: {}ms", totalTimeMs);
        log.info("Throughput: {:.2f} trades/second", throughput);
        log.info("Average latency: {:.2f}ms", avgLatency);
        
        assertTrue(throughput > 5, "Concurrent throughput should be > 5 trades/second");
    }
}
