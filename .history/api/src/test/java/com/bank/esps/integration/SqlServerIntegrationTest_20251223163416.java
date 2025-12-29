package com.bank.esps.integration;

import com.bank.esps.application.service.HotpathPositionService;
import com.bank.esps.application.service.TradeProcessingService;
import com.bank.esps.application.util.PositionKeyGenerator;
import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.Contract;
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
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for MS SQL Server database
 * Tests that the system works correctly with MS SQL Server
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SqlServerIntegrationTest {
    
    @Container
    static MSSQLServerContainer<?> sqlServer = new MSSQLServerContainer<>(
            DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
            .acceptLicense()
            .withDatabaseName("test_db")
            .withUsername("SA")
            .withPassword("Test@123456");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.type", () -> "sqlserver");
        registry.add("spring.datasource.url", sqlServer::getJdbcUrl);
        registry.add("spring.datasource.username", sqlServer::getUsername);
        registry.add("spring.datasource.password", sqlServer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 6379);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/sqlserver");
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
    
    @Autowired
    private com.bank.esps.application.service.ContractRulesService contractRulesService;
    
    @Autowired
    private com.bank.esps.infrastructure.persistence.repository.IdempotencyRepository idempotencyRepository;
    
    @Autowired
    private com.bank.esps.domain.cache.CacheService cacheService;
    
    private String positionKey;
    private String account;
    private String instrument = "AAPL";
    private String currency = "USD";
    private static int testCounter = 0;
    
    @BeforeEach
    void setUp() {
        testCounter++;
        account = "SQLSRV-ACC-" + String.format("%03d", testCounter);
        positionKey = positionKeyGenerator.generatePositionKey(account, instrument, currency, false);
        
        // Clear idempotency cache
        idempotencyRepository.deleteAll();
        if (cacheService != null) {
            cacheService.clear();
        }
    }
    
    @Test
    void testCreatePositionWithSqlServer() {
        // Setup contract
        Contract contract = Contract.builder()
                .contractId("SQLSRV-CONTRACT-001")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        contractRulesService.updateContract(contract);
        
        // Create trade event
        TradeEvent tradeEvent = TradeEvent.builder()
                .tradeId("SQLSRV-TRADE-" + System.currentTimeMillis())
                .positionKey(positionKey)
                .account(account)
                .instrument(instrument)
                .currency(currency)
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("150.50"))
                .effectiveDate(LocalDate.now())
                .contractId("SQLSRV-CONTRACT-001")
                .correlationId("SQLSRV-CORR-001")
                .causationId("SQLSRV-CAUS-001")
                .userId("test-user")
                .build();
        
        // Process trade
        tradeProcessingService.processTrade(tradeEvent);
        
        // Verify position was created
        Optional<SnapshotEntity> snapshotOpt = snapshotRepository.findById(positionKey);
        assertTrue(snapshotOpt.isPresent(), "Position should be created in MS SQL Server");
        
        SnapshotEntity snapshot = snapshotOpt.get();
        assertEquals(account, snapshot.getAccount(), "Account should be stored");
        assertEquals(instrument, snapshot.getInstrument(), "Instrument should be stored");
        assertEquals(currency, snapshot.getCurrency(), "Currency should be stored");
        assertEquals("SQLSRV-CONTRACT-001", snapshot.getContractId(), "Contract ID should be stored");
        
        // Verify event was stored
        long eventCount = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey).size();
        assertEquals(1, eventCount, "Event should be stored in MS SQL Server");
        
        System.out.println("✅ MS SQL Server integration test passed!");
        System.out.println("   Position Key: " + positionKey);
        System.out.println("   Account: " + snapshot.getAccount());
        System.out.println("   Instrument: " + snapshot.getInstrument());
        System.out.println("   Currency: " + snapshot.getCurrency());
    }
    
    @Test
    void testPositionLifecycleWithSqlServer() {
        // Setup contract
        Contract contract = Contract.builder()
                .contractId("SQLSRV-CONTRACT-002")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        contractRulesService.updateContract(contract);
        
        LocalDate baseDate = LocalDate.now();
        
        // 1. Create position
        TradeEvent newTrade = TradeEvent.builder()
                .tradeId("SQLSRV-LIFECYCLE-NEW-" + System.currentTimeMillis())
                .positionKey(positionKey)
                .account(account)
                .instrument(instrument)
                .currency(currency)
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("150.50"))
                .effectiveDate(baseDate)
                .contractId("SQLSRV-CONTRACT-002")
                .correlationId("SQLSRV-CORR-002")
                .causationId("SQLSRV-CAUS-002")
                .userId("test-user")
                .build();
        
        tradeProcessingService.processTrade(newTrade);
        
        // 2. Increase position
        TradeEvent increase = TradeEvent.builder()
                .tradeId("SQLSRV-LIFECYCLE-INCREASE-" + System.currentTimeMillis())
                .positionKey(positionKey)
                .account(account)
                .instrument(instrument)
                .currency(currency)
                .tradeType("INCREASE")
                .quantity(new BigDecimal("500"))
                .price(new BigDecimal("155.00"))
                .effectiveDate(baseDate.plusDays(1))
                .contractId("SQLSRV-CONTRACT-002")
                .correlationId("SQLSRV-CORR-002")
                .causationId("SQLSRV-CORR-002")
                .userId("test-user")
                .build();
        
        tradeProcessingService.processTrade(increase);
        
        // 3. Verify final state
        Optional<SnapshotEntity> snapshotOpt = snapshotRepository.findById(positionKey);
        assertTrue(snapshotOpt.isPresent());
        
        SnapshotEntity snapshot = snapshotOpt.get();
        assertNotNull(snapshot.getAccount(), "Account should be populated");
        assertNotNull(snapshot.getInstrument(), "Instrument should be populated");
        assertNotNull(snapshot.getCurrency(), "Currency should be populated");
        
        System.out.println("✅ MS SQL Server lifecycle test passed!");
    }
}
