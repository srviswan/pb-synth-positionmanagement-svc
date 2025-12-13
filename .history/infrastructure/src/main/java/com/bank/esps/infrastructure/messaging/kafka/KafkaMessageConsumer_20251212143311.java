package com.bank.esps.infrastructure.messaging.kafka;

import com.bank.esps.domain.messaging.MessageConsumer;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka implementation of MessageConsumer
 */
@Component("kafkaMessageConsumer")
public class KafkaMessageConsumer implements MessageConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaMessageConsumer.class);
    
    private final ObjectMapper objectMapper;
    private Consumer<TradeEvent> backdatedTradeProcessor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    public KafkaMessageConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void subscribeToBackdatedTrades(Consumer<TradeEvent> processor) {
        this.backdatedTradeProcessor = processor;
        log.info("Registered backdated trade processor");
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
        
        if (!running.get()) {
            log.debug("Consumer stopped, ignoring message");
            return;
        }
        
        try {
            log.info("Received backdated trade: partition={}, offset={}, key={}", partition, offset, key);
            
            // Deserialize trade event
            TradeEvent tradeEvent = objectMapper.readValue(message, TradeEvent.class);
            
            // Process backdated trade (if processor is set)
            if (backdatedTradeProcessor != null) {
                backdatedTradeProcessor.accept(tradeEvent);
            } else {
                log.warn("Backdated trade processor not set, skipping trade {}", tradeEvent.getTradeId());
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
    
    @Override
    public void start() {
        running.set(true);
        log.info("Kafka message consumer started");
    }
    
    @Override
    public void stop() {
        running.set(false);
        log.info("Kafka message consumer stopped");
    }
    
    @Override
    public boolean isRunning() {
        return running.get();
    }
}
