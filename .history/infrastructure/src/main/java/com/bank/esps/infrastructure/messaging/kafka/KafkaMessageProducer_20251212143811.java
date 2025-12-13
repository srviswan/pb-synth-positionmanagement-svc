package com.bank.esps.infrastructure.messaging.kafka;

import com.bank.esps.domain.messaging.MessageProducer;
import com.bank.esps.domain.event.TradeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka implementation of MessageProducer
 * Enabled when app.messaging.kafka.enabled=true (default) or app.messaging.type=kafka
 */
@Component("kafkaMessageProducer")
@ConditionalOnProperty(
        name = "app.messaging.kafka.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class KafkaMessageProducer implements MessageProducer {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaMessageProducer.class);
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public KafkaMessageProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public CompletableFuture<Void> publishBackdatedTrade(TradeEvent tradeEvent) {
        try {
            String message = objectMapper.writeValueAsString(tradeEvent);
            String topic = "backdated-trades";
            String key = tradeEvent.getPositionKey();
            
            CompletableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(topic, key, message);
            
            return future.handle((result, ex) -> {
                if (ex == null) {
                    log.debug("Published backdated trade {} to topic {}", tradeEvent.getTradeId(), topic);
                    return null;
                } else {
                    log.error("Failed to publish backdated trade {} to topic {}", 
                            tradeEvent.getTradeId(), topic, ex);
                    throw new RuntimeException("Failed to publish backdated trade", ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Error publishing backdated trade {}", tradeEvent.getTradeId(), e);
            CompletableFuture<Void> errorFuture = new CompletableFuture<>();
            errorFuture.completeExceptionally(new RuntimeException("Failed to publish backdated trade", e));
            return errorFuture;
        }
    }
    
    @Override
    public CompletableFuture<Void> publishToDLQ(TradeEvent tradeEvent, String error) {
        try {
            String message = objectMapper.writeValueAsString(tradeEvent);
            String topic = "trade-events-dlq";
            String key = tradeEvent.getTradeId();
            
            CompletableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(topic, key, message);
            
            return future.handle((result, ex) -> {
                if (ex == null) {
                    log.warn("Published trade {} to DLQ: {}", tradeEvent.getTradeId(), error);
                    return null;
                } else {
                    log.error("Error publishing to DLQ for trade {}", tradeEvent.getTradeId(), ex);
                    return null; // Don't fail on DLQ publish errors
                }
            });
            
        } catch (Exception e) {
            log.error("Error publishing to DLQ for trade {}", tradeEvent.getTradeId(), e);
            return CompletableFuture.completedFuture(null); // Don't fail on DLQ publish errors
        }
    }
    
    @Override
    public CompletableFuture<Void> publishToErrorQueue(TradeEvent tradeEvent, String error) {
        try {
            String message = objectMapper.writeValueAsString(tradeEvent);
            String topic = "trade-events-errors";
            String key = tradeEvent.getTradeId();
            
            CompletableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(topic, key, message);
            
            return future.handle((result, ex) -> {
                if (ex == null) {
                    log.warn("Published trade {} to error queue: {}", tradeEvent.getTradeId(), error);
                    return null;
                } else {
                    log.error("Error publishing to error queue for trade {}", tradeEvent.getTradeId(), ex);
                    return null; // Don't fail on error queue publish errors
                }
            });
            
        } catch (Exception e) {
            log.error("Error publishing to error queue for trade {}", tradeEvent.getTradeId(), e);
            return CompletableFuture.completedFuture(null); // Don't fail on error queue publish errors
        }
    }
    
    @Override
    public CompletableFuture<Void> publishCorrectionEvent(String positionKey, String correctionEvent) {
        try {
            String topic = "historical-position-corrected-events";
            CompletableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(topic, positionKey, correctionEvent);
            
            return future.handle((result, ex) -> {
                if (ex == null) {
                    log.debug("Published correction event for position {}", positionKey);
                    return null;
                } else {
                    log.error("Error publishing correction event for position {}", positionKey, ex);
                    return null; // Don't fail on correction event publish errors
                }
            });
            
        } catch (Exception e) {
            log.error("Error publishing correction event for position {}", positionKey, e);
            return CompletableFuture.completedFuture(null); // Don't fail on correction event publish errors
        }
    }
}
