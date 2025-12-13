package com.bank.esps.infrastructure.kafka;

import com.bank.esps.domain.event.TradeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for trade events
 * Publishes to various topics (backdated-trades, DLQ, etc.)
 */
@Component
public class TradeEventProducer {
    
    private static final Logger log = LoggerFactory.getLogger(TradeEventProducer.class);
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public TradeEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Publish backdated trade to coldpath topic
     */
    public void publishBackdatedTrade(TradeEvent tradeEvent) {
        try {
            String message = objectMapper.writeValueAsString(tradeEvent);
            String topic = "backdated-trades";
            String key = tradeEvent.getPositionKey();
            
            CompletableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(topic, key, message);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Published backdated trade {} to topic {}", tradeEvent.getTradeId(), topic);
                } else {
                    log.error("Failed to publish backdated trade {} to topic {}", 
                            tradeEvent.getTradeId(), topic, ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Error publishing backdated trade {}", tradeEvent.getTradeId(), e);
            throw new RuntimeException("Failed to publish backdated trade", e);
        }
    }
    
    /**
     * Publish to Dead Letter Queue
     */
    public void publishToDLQ(TradeEvent tradeEvent, String error) {
        try {
            String message = objectMapper.writeValueAsString(tradeEvent);
            String topic = "trade-events-dlq";
            String key = tradeEvent.getTradeId();
            
            // Add error to message metadata
            // In production, would use a wrapper object with error details
            
            kafkaTemplate.send(topic, key, message);
            log.warn("Published trade {} to DLQ: {}", tradeEvent.getTradeId(), error);
            
        } catch (Exception e) {
            log.error("Error publishing to DLQ for trade {}", tradeEvent.getTradeId(), e);
        }
    }
    
    /**
     * Publish to error queue (for retryable errors)
     */
    public void publishToErrorQueue(TradeEvent tradeEvent, String error) {
        try {
            String message = objectMapper.writeValueAsString(tradeEvent);
            String topic = "trade-events-errors";
            String key = tradeEvent.getTradeId();
            
            kafkaTemplate.send(topic, key, message);
            log.warn("Published trade {} to error queue: {}", tradeEvent.getTradeId(), error);
            
        } catch (Exception e) {
            log.error("Error publishing to error queue for trade {}", tradeEvent.getTradeId(), e);
        }
    }
    
    /**
     * Publish correction event (coldpath output)
     */
    public void publishCorrectionEvent(String positionKey, String correctionEvent) {
        try {
            String topic = "historical-position-corrected-events";
            kafkaTemplate.send(topic, positionKey, correctionEvent);
            log.debug("Published correction event for position {}", positionKey);
        } catch (Exception e) {
            log.error("Error publishing correction event for position {}", positionKey, e);
        }
    }
}
