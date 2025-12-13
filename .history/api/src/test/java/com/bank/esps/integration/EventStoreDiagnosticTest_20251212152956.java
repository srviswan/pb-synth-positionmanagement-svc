package com.bank.esps.integration;

import com.bank.esps.application.service.TradeProcessingService;
import com.bank.esps.domain.enums.EventType;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic test to verify event store is working
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("e2e")
class EventStoreDiagnosticTest {
    
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
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
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
    private EventStoreRepository eventStoreRepository;
    
    @Test
    void testEventStore_SaveAndRetrieve() {
        // Given: A new trade
        String positionKey = "diagnostic-test-" + System.currentTimeMillis();
        TradeEvent trade = TradeEvent.builder()
                .tradeId("T-DIAG-001")
                .positionKey(positionKey)
                .tradeType("NEW_TRADE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("50.00"))
                .effectiveDate(LocalDate.now())
                .contractId("C-DIAG-001")
                .correlationId("CORR-DIAG-001")
                .build();
        
        // When: Process the trade
        System.out.println("=== Processing trade ===");
        tradeProcessingService.processTrade(trade);
        System.out.println("=== Trade processed ===");
        
        // Then: Verify event was stored
        System.out.println("=== Checking event store ===");
        List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
        
        System.out.println("Found " + events.size() + " events for position " + positionKey);
        
        assertFalse(events.isEmpty(), "Event should be stored - found " + events.size() + " events");
        
        EventEntity event = events.get(0);
        System.out.println("Event details:");
        System.out.println("  Position Key: " + event.getPositionKey());
        System.out.println("  Version: " + event.getEventVer());
        System.out.println("  Type: " + event.getEventType());
        System.out.println("  Effective Date: " + event.getEffectiveDate());
        System.out.println("  Occurred At: " + event.getOccurredAt());
        System.out.println("  Payload Length: " + (event.getPayload() != null ? event.getPayload().length() : 0));
        
        assertEquals(positionKey, event.getPositionKey());
        assertEquals(1L, event.getEventVer());
        assertEquals(EventType.NEW_TRADE, event.getEventType());
        
        // Verify we can query by ID
        EventEntity foundById = eventStoreRepository.findById(
                new EventEntity.EventEntityId(positionKey, 1L)).orElse(null);
        assertNotNull(foundById, "Event should be findable by ID");
        assertEquals(event.getEventVer(), foundById.getEventVer());
        
        System.out.println("=== Event store test PASSED ===");
    }
    
    @Test
    void testEventStore_Count() {
        // Get total count
        long totalCount = eventStoreRepository.count();
        System.out.println("Total events in event store: " + totalCount);
        
        // This should be at least 0
        assertTrue(totalCount >= 0, "Event count should be non-negative");
    }
}
