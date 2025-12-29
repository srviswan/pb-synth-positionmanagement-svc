package com.bank.esps.infrastructure.messaging.solace;

import com.bank.esps.domain.messaging.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Solace implementation of MessageConsumer
 */
@Component("solaceMessageConsumer")
public class SolaceMessageConsumer implements MessageConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(SolaceMessageConsumer.class);
    
    private final Map<String, Consumer<String>> handlers = new ConcurrentHashMap<>();
    
    @Override
    public void subscribe(String topic, Consumer<String> messageHandler) {
        handlers.put(topic, messageHandler);
        log.info("Subscribed to Solace topic: {}", topic);
    }
    
    @Override
    public void unsubscribe(String topic) {
        handlers.remove(topic);
        log.info("Unsubscribed from Solace topic: {}", topic);
    }
    
    /**
     * Process a message from Solace
     */
    @JmsListener(destination = "${solace.topic.prefix:}")
    public void processMessage(String message) {
        // Extract topic from message headers and route to appropriate handler
        log.debug("Received message from Solace");
    }
}
