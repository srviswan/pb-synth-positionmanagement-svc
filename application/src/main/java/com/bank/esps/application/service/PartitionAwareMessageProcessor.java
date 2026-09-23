package com.bank.esps.application.service;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.messaging.PartitionAwareMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for processing partition-aware messages
 * Works with both Kafka and Solace messaging providers
 */
@Service
public class PartitionAwareMessageProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(PartitionAwareMessageProcessor.class);
    
    private final ObjectMapper objectMapper;
    
    public PartitionAwareMessageProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Process a partition-aware message containing a trade event
     * Extracts partition information and logs it for monitoring
     */
    public void processPartitionAwareTradeMessage(PartitionAwareMessage message, 
                                                  java.util.function.Consumer<TradeEvent> tradeProcessor) {
        try {
            TradeEvent tradeEvent = objectMapper.readValue(message.getMessageBody(), TradeEvent.class);
            
            // Log partition information
            if (message.hasPartitionInfo()) {
                log.info("Processing trade from partition-aware message: tradeId={}, positionKey={}, " +
                        "partitionId={}, partitionKey={}, topic={}, offset={}", 
                        tradeEvent.getTradeId(), 
                        tradeEvent.getPositionKey(),
                        message.getPartitionId(),
                        message.getPartitionKey(),
                        message.getTopic(),
                        message.getOffset());
            } else {
                log.info("Processing trade from message: tradeId={}, positionKey={}, topic={}", 
                        tradeEvent.getTradeId(), 
                        tradeEvent.getPositionKey(),
                        message.getTopic());
            }
            
            // Process the trade
            tradeProcessor.accept(tradeEvent);
            
        } catch (Exception e) {
            log.error("Failed to process partition-aware trade message: partitionId={}, partitionKey={}", 
                    message.getPartitionId(), message.getPartitionKey(), e);
            throw new RuntimeException("Failed to process partition-aware trade message", e);
        }
    }
    
    /**
     * Extract partition information from message for metrics/logging
     */
    public String getPartitionInfo(PartitionAwareMessage message) {
        if (!message.hasPartitionInfo()) {
            return "no-partition-info";
        }
        
        if (message.getPartitionId() != null) {
            return "partition-" + message.getPartitionId();
        } else if (message.getPartitionKey() != null) {
            return "key-" + message.getPartitionKey();
        }
        
        return "unknown";
    }
}
