package com.bank.esps.api.controller;

import com.bank.esps.application.service.SnapshotService;
import com.bank.esps.application.util.PositionKeyGenerator;
import com.bank.esps.domain.enums.PositionStatus;
import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.Contract;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import com.bank.esps.application.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for PositionController endpoints
 * Tests all REST endpoints including new paginated lookup endpoints
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class PositionControllerTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:14-alpine"))
            .withDatabaseName("test_db")
            .withUsername("postgres")
            .withPassword("postgres");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 6379);
    }
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private SnapshotRepository snapshotRepository;
    
    @Autowired
    private SnapshotService snapshotService;
    
    @Autowired
    private HotpathPositionService hotpathPositionService;
    
    @Autowired
    private ContractRulesService contractRulesService;
    
    @Autowired
    private PositionKeyGenerator positionKeyGenerator;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String testAccount = "TEST-ACC-001";
    private String testInstrument = "AAPL";
    private String testCurrency = "USD";
    private String testContractId = "TEST-CONTRACT-001";
    private String positionKey;
    
    @BeforeEach
    void setUp() {
        // Generate position key
        positionKey = positionKeyGenerator.generatePositionKey(testAccount, testInstrument, testCurrency, false);
        
        // Setup contract
        Contract contract = Contract.builder()
                .contractId(testContractId)
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        contractRulesService.updateContract(contract);
        
        // Create a test position
        TradeEvent tradeEvent = TradeEvent.builder()
                .tradeId("TEST-TRADE-" + System.currentTimeMillis())
                .positionKey(positionKey)
                .account(testAccount)
                .instrument(testInstrument)
                .currency(testCurrency)
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("150.50"))
                .effectiveDate(LocalDate.now())
                .contractId(testContractId)
                .correlationId("TEST-CORR-001")
                .causationId("TEST-CAUS-001")
                .userId("test-user")
                .build();
        
        hotpathPositionService.processCurrentDatedTrade(tradeEvent);
        
        // Verify lookup fields are set
        SnapshotEntity snapshot = snapshotRepository.findById(positionKey).orElseThrow();
        if (snapshot.getAccount() == null) {
            // Manually set lookup fields if not set (for testing)
            snapshot.setAccount(testAccount);
            snapshot.setInstrument(testInstrument);
            snapshot.setCurrency(testCurrency);
            snapshot.setContractId(testContractId);
            snapshotRepository.save(snapshot);
        }
    }
    
    @Test
    void testGetPositionByKey() throws Exception {
        mockMvc.perform(get("/api/positions/{positionKey}", positionKey))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.positionKey").value(positionKey))
                .andExpect(jsonPath("$.quantity").exists())
                .andExpect(jsonPath("$.positionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.account").value(testAccount))
                .andExpect(jsonPath("$.instrument").value(testInstrument))
                .andExpect(jsonPath("$.currency").value(testCurrency))
                .andExpect(jsonPath("$.contractId").value(testContractId));
    }
    
    @Test
    void testGetPositionByKeyNotFound() throws Exception {
        mockMvc.perform(get("/api/positions/{positionKey}", "non-existent-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("not_found"));
    }
    
    @Test
    void testGetPositionQuantity() throws Exception {
        mockMvc.perform(get("/api/positions/{positionKey}/quantity", positionKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.quantity").exists())
                .andExpect(jsonPath("$.openLots").exists())
                .andExpect(jsonPath("$.positionStatus").value("ACTIVE"));
    }
    
    @Test
    void testGetPositionDetails() throws Exception {
        mockMvc.perform(get("/api/positions/{positionKey}/details", positionKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.positionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.taxLots").isArray())
                .andExpect(jsonPath("$.openLots").exists())
                .andExpect(jsonPath("$.closedLots").exists());
    }
    
    @Test
    void testGetAllPositionsPaginated() throws Exception {
        mockMvc.perform(get("/api/positions")
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "lastUpdatedAt")
                .param("sortDir", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(10))
                .andExpect(jsonPath("$.pagination.totalElements").exists())
                .andExpect(jsonPath("$.positions").isArray());
    }
    
    @Test
    void testGetPositionsByAccount() throws Exception {
        mockMvc.perform(get("/api/positions/by-account/{account}", testAccount)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.positions").isArray())
                .andExpect(jsonPath("$.positions.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.positions[0].account").value(testAccount));
    }
    
    @Test
    void testGetPositionsByAccountWithStatus() throws Exception {
        mockMvc.perform(get("/api/positions/by-account/{account}", testAccount)
                .param("status", "ACTIVE")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.positions").isArray())
                .andExpect(jsonPath("$.positions.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.positions[0].status").value("ACTIVE"));
    }
    
    @Test
    void testGetPositionsByInstrument() throws Exception {
        mockMvc.perform(get("/api/positions/by-instrument/{instrument}", testInstrument)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.positions").isArray())
                .andExpect(jsonPath("$.positions.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.positions[0].instrument").value(testInstrument));
    }
    
    @Test
    void testGetPositionsByAccountAndInstrument() throws Exception {
        mockMvc.perform(get("/api/positions/by-account/{account}/instrument/{instrument}", 
                testAccount, testInstrument)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.positions").isArray())
                .andExpect(jsonPath("$.positions.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.positions[0].account").value(testAccount))
                .andExpect(jsonPath("$.positions[0].instrument").value(testInstrument));
    }
    
    @Test
    void testGetPositionsByAccountInstrumentAndCurrency() throws Exception {
        mockMvc.perform(get("/api/positions/by-account/{account}/instrument/{instrument}", 
                testAccount, testInstrument)
                .param("currency", testCurrency)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.positions").isArray())
                .andExpect(jsonPath("$.positions.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.positions[0].currency").value(testCurrency));
    }
    
    @Test
    void testGetPositionsByContract() throws Exception {
        mockMvc.perform(get("/api/positions/by-contract/{contractId}", testContractId)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.positions").isArray())
                .andExpect(jsonPath("$.positions.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.positions[0].contractId").value(testContractId));
    }
    
    @Test
    void testGetPositionByUPI() throws Exception {
        // Get UPI from the position
        SnapshotEntity snapshot = snapshotRepository.findById(positionKey).orElseThrow();
        String upi = snapshot.getUti();
        
        mockMvc.perform(get("/api/positions/upi/{upi}", upi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.positionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.upi").value(upi))
                .andExpect(jsonPath("$.positionKey").value(positionKey));
    }
    
    @Test
    void testGetPositionByUPINotFound() throws Exception {
        mockMvc.perform(get("/api/positions/upi/{upi}", "non-existent-upi"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("not_found"));
    }
    
    @Test
    void testPaginationParameters() throws Exception {
        mockMvc.perform(get("/api/positions/by-account/{account}", testAccount)
                .param("page", "0")
                .param("size", "5")
                .param("sortBy", "lastUpdatedAt")
                .param("sortDir", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(5))
                .andExpect(jsonPath("$.pagination.page").value(0));
    }
    
    @Test
    void testInvalidStatusParameter() throws Exception {
        mockMvc.perform(get("/api/positions/by-account/{account}", testAccount)
                .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }
    
    @Test
    void testPaginationMaxSize() throws Exception {
        // Test that size is capped at 100
        mockMvc.perform(get("/api/positions")
                .param("page", "0")
                .param("size", "200"))  // Request 200, should be capped at 100
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(lessThanOrEqualTo(100)));
    }
}
