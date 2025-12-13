package com.bank.esps.infrastructure.messaging.solace;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.messaging.MessageProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Solace implementation of MessageProducer
 * 
 * This is an EXAMPLE implementation showing how to switch from Kafka to Solace.
 * To use this:
 * 1. Add Solace dependencies to pom.xml
 * 2. Configure Solace connection properties
 * 3. Set spring.profiles.active=solace
 * 
 * Note: This is a template - actual Solace integration would require:
 * - Solace JMS dependencies
 * - Connection factory configuration
 * - Topic/Queue configuration
 */
@Component("solaceMessageProducer")
@Profile("solace")
public class SolaceMessageProducer implements MessageProducer {
    
    private static final Logger log = LoggerFactory.getLogger(SolaceMessageProducer.class);
    
    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;
    
    public SolaceMessageProducer(JmsTemplate jmsTemplate, ObjectMapper objectMapper) {
        this.jmsTemplate = jmsTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public CompletableFuture<Void> publishBackdatedTrade(TradeEvent tradeEvent) {
        try {
            String message = objectMapper.writeValueAsString(tradeEvent);
            String topic = "backdated-trades";
            
            // Solace uses JMS - send to topic
            jmsTemplate.convertAndSend(topic, message);
            
            log.debug("Published backdated trade {} to Solace topic {}", tradeEvent.getTradeId(), topic);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error publishing backdated trade {} to Solace", tradeEvent.getTradeId(), e);
            CompletableFuture<Void> errorFuture = new CompletableFuture<>();
            errorFuture.completeExceptionally(new RuntimeException("Failed to publish backdated trade to Solace", e));
            return errorFuture;
        }
    }
    
    @Override
    public CompletableFuture<Void> publishToDLQ(TradeEvent tradeEvent, String error) {
        try {
            String message = objectMapper.writeValueAsString(tradeEvent);
            String queue = "trade-events-dlq";
            
            jmsTemplate.convertAndSend(queue, message);
            log.warn("Published trade {} to Solace DLQ: {}", tradeEvent.getTradeId(), error);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error publishing to Solace DLQ for trade {}", tradeEvent.getTradeId(), e);
            return CompletableFuture.completedFuture(null); // Don't fail on DLQ publish errors
        }
    }
    
    @Override
    public CompletableFuture<Void> publishToErrorQueue(TradeEvent tradeEvent, String error) {
        try {
            String message = objectMapper.writeValueAsString(tradeEvent);
            String queue = "trade-events-errors";
            
            jmsTemplate.convertAndSend(queue, message);
            log.warn("Published trade {} to Solace error queue: {}", tradeEvent.getTradeId(), error);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error publishing to Solace error queue for trade {}", tradeEvent.getTradeId(), e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    @Override
    public CompletableFuture<Void> publishCorrectionEvent(String positionKey, String correctionEvent) {
        try {
            String topic = "historical-position-corrected-events";
            
            jmsTemplate.convertAndSend(topic, correctionEvent);
            log.debug("Published correction event for position {} to Solace", positionKey);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error publishing correction event to Solace for position {}", positionKey, e);
            return CompletableFuture.completedFuture(null);
        }
    }
}
