package com.bank.esps.infrastructure.kafka;

import com.bank.esps.domain.event.TradeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Kafka consumer for backdated trades (Coldpath)
 * Consumes from backdated-trades topic
 * Uses a functional interface to avoid circular dependency
 */
@Component
public class BackdatedTradeConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(BackdatedTradeConsumer.class);
    
    private final ObjectMapper objectMapper;
    private Consumer<TradeEvent> recalculationProcessor; // Injected by application module
    
    public BackdatedTradeConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Set the recalculation processor (called by application module)
     */
    public void setRecalculationProcessor(Consumer<TradeEvent> processor) {
        this.recalculationProcessor = processor;
    }
    
    @KafkaListener(
            topics = "backdated-trades",
            groupId = "${spring.kafka.consumer.coldpath-group-id:coldpath-recalculation-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeBackdatedTrade(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Received backdated trade: partition={}, offset={}, key={}", partition, offset, key);
            
            // Deserialize trade event
            TradeEvent tradeEvent = objectMapper.readValue(message, TradeEvent.class);
            
            // Process backdated trade (if processor is set)
            if (recalculationProcessor != null) {
                recalculationProcessor.accept(tradeEvent);
            } else {
                log.warn("Recalculation processor not set, skipping backdated trade {}", tradeEvent.getTradeId());
            }
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed backdated trade: {}", tradeEvent.getTradeId());
            
        } catch (Exception e) {
            log.error("Error processing backdated trade: partition={}, offset={}, key={}", 
                    partition, offset, key, e);
            // In production, would route to DLQ after max retries
            // For now, acknowledge to prevent blocking (should be configurable)
            acknowledgment.acknowledge();
        }
    }
}
