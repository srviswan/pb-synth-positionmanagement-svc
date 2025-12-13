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
 * Kafka consumer for trade events (Hotpath)
 * Consumes from trade-events topic
 * Uses a functional interface to avoid circular dependency
 */
@Component
public class TradeEventConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(TradeEventConsumer.class);
    
    private final ObjectMapper objectMapper;
    private Consumer<TradeEvent> tradeProcessor; // Injected by application module
    
    public TradeEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Set the trade processor (called by application module)
     */
    public void setTradeProcessor(Consumer<TradeEvent> processor) {
        this.tradeProcessor = processor;
    }
    
    @KafkaListener(
            topics = "trade-events",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTradeEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("Received trade event: partition={}, offset={}, key={}", partition, offset, key);
            
            // Deserialize trade event
            TradeEvent tradeEvent = objectMapper.readValue(message, TradeEvent.class);
            
            // Process trade (if processor is set)
            if (tradeProcessor != null) {
                tradeProcessor.accept(tradeEvent);
            } else {
                log.warn("Trade processor not set, skipping trade {}", tradeEvent.getTradeId());
            }
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed trade event: {}", tradeEvent.getTradeId());
            
        } catch (Exception e) {
            log.error("Error processing trade event: partition={}, offset={}, key={}", 
                    partition, offset, key, e);
            // In production, would route to DLQ
            // For now, acknowledge to prevent blocking
            acknowledgment.acknowledge();
        }
    }
}
