package com.bank.esps.infrastructure.kafka;

import com.bank.esps.application.service.TradeProcessingService;
import com.bank.esps.domain.event.TradeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for trade events (Hotpath)
 * Consumes from trade-events topic
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeEventConsumer {
    
    private final TradeProcessingService tradeProcessingService;
    private final ObjectMapper objectMapper;
    
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
            
            // Process trade
            tradeProcessingService.processTrade(tradeEvent);
            
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
