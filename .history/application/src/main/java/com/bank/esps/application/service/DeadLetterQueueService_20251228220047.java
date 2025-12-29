package com.bank.esps.application.service;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.messaging.MessageProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for routing failed trades to Dead Letter Queue (DLQ)
 */
@Service
public class DeadLetterQueueService {
    
    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueService.class);
    
    private final MessageProducer messageProducer;
    private final ObjectMapper objectMapper;
    
    @Value("${app.kafka.topics.dlq:trade-events-dlq}")
    private String dlqTopic;
    
    @Value("${app.kafka.topics.error-queue:trade-events-errors}")
    private String errorQueueTopic;
    
    public DeadLetterQueueService(MessageProducer messageProducer, ObjectMapper objectMapper) {
        this.messageProducer = messageProducer;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Route failed trade to Dead Letter Queue
     */
    public void routeToDLQ(TradeEvent tradeEvent, List<String> validationErrors, String errorType) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("tradeEvent", tradeEvent);
            dlqMessage.put("validationErrors", validationErrors);
            dlqMessage.put("errorType", errorType);
            dlqMessage.put("timestamp", java.time.OffsetDateTime.now().toString());
            dlqMessage.put("correlationId", org.slf4j.MDC.get("correlationId"));
            
            String messageJson = objectMapper.writeValueAsString(dlqMessage);
            String key = tradeEvent.getPositionKey() != null 
                    ? tradeEvent.getPositionKey() 
                    : tradeEvent.getTradeId();
            
            messageProducer.send(dlqTopic, key, messageJson);
            log.warn("Routed trade {} to DLQ: {}", tradeEvent.getTradeId(), validationErrors);
        } catch (Exception e) {
            log.error("Failed to route trade {} to DLQ", tradeEvent.getTradeId(), e);
        }
    }
    
    /**
     * Route recoverable error to error queue for retry
     */
    public void routeToErrorQueue(TradeEvent tradeEvent, String errorMsg, boolean retryable) {
        try {
            Map<String, Object> errorMessage = new HashMap<>();
            errorMessage.put("tradeEvent", tradeEvent);
            errorMessage.put("errorMessage", errorMsg);
            errorMessage.put("retryable", retryable);
            errorMessage.put("timestamp", java.time.OffsetDateTime.now().toString());
            errorMessage.put("correlationId", org.slf4j.MDC.get("correlationId"));
            
            String messageJson = objectMapper.writeValueAsString(errorMessage);
            String key = tradeEvent.getPositionKey() != null 
                    ? tradeEvent.getPositionKey() 
                    : tradeEvent.getTradeId();
            
            messageProducer.send(errorQueueTopic, key, messageJson);
            log.warn("Routed trade {} to error queue (retryable: {}): {}", 
                    tradeEvent.getTradeId(), retryable, errorMsg);
        } catch (Exception e) {
            log.error("Failed to route trade {} to error queue", tradeEvent.getTradeId(), e);
        }
    }
}
