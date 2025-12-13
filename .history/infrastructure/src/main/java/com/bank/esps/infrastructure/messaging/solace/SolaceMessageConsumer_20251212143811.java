package com.bank.esps.infrastructure.messaging.solace;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.messaging.MessageConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Note: JMS imports commented out - requires Solace JMS dependencies
// import org.springframework.jms.annotation.JmsListener;
// import org.springframework.jms.core.JmsTemplate;

import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Solace implementation of MessageConsumer
 * 
 * Enabled when app.messaging.solace.enabled=true or app.messaging.type=solace
 */
@Component("solaceMessageConsumer")
@ConditionalOnProperty(
        name = "app.messaging.solace.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class SolaceMessageConsumer implements MessageConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(SolaceMessageConsumer.class);
    
    private final ObjectMapper objectMapper;
    // private final JmsTemplate jmsTemplate; // Requires Solace JMS dependencies
    private Consumer<TradeEvent> backdatedTradeProcessor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public SolaceMessageConsumer(ObjectMapper objectMapper /*, JmsTemplate jmsTemplate */) {
        this.objectMapper = objectMapper;
        // this.jmsTemplate = jmsTemplate;
    }
    
    @Override
    public void subscribeToBackdatedTrades(Consumer<TradeEvent> processor) {
        this.backdatedTradeProcessor = processor;
        log.info("Registered backdated trade processor for Solace");
    }
    
    // @JmsListener(destination = "backdated-trades", containerFactory = "jmsListenerContainerFactory")
    // TODO: Uncomment and implement with actual Solace JMS when dependencies are added
    public void consumeBackdatedTrade(String message) {
        if (!running.get()) {
            log.debug("Solace consumer stopped, ignoring message");
            return;
        }
        
        try {
            log.info("Received backdated trade from Solace");
            
            // Deserialize trade event
            TradeEvent tradeEvent = objectMapper.readValue(message, TradeEvent.class);
            
            // Process backdated trade (if processor is set)
            if (backdatedTradeProcessor != null) {
                backdatedTradeProcessor.accept(tradeEvent);
            } else {
                log.warn("Backdated trade processor not set, skipping trade {}", tradeEvent.getTradeId());
            }
            
            log.info("Successfully processed backdated trade: {}", tradeEvent.getTradeId());
            
        } catch (Exception e) {
            log.error("Error processing backdated trade from Solace", e);
            // In production, would route to DLQ after max retries
        }
    }
    
    @Override
    public void start() {
        running.set(true);
        log.info("Solace message consumer started");
    }
    
    @Override
    public void stop() {
        running.set(false);
        log.info("Solace message consumer stopped");
    }
    
    @Override
    public boolean isRunning() {
        return running.get();
    }
}
