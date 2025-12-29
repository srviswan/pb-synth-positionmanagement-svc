package com.bank.esps.infrastructure.messaging.solace;

import com.bank.esps.domain.messaging.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Solace implementation of MessageConsumer
 * Note: Requires Solace JMS dependencies to be added when using Solace
 */
@Component("solaceMessageConsumer")
public class SolaceMessageConsumer implements MessageConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(SolaceMessageConsumer.class);
    
    private final Map<String, Consumer<String>> handlers = new ConcurrentHashMap<>();
    
    public SolaceMessageConsumer() {
        log.warn("SolaceMessageConsumer initialized but Solace dependencies not available. Add solace-jms-spring-boot-starter to use Solace.");
    }
    
    @Override
    public void subscribe(String topic, Consumer<String> messageHandler) {
        handlers.put(topic, messageHandler);
        log.warn("Solace not configured. Subscription to topic: {} not active", topic);
    }
    
    @Override
    public void unsubscribe(String topic) {
        handlers.remove(topic);
        log.info("Unsubscribed from Solace topic: {}", topic);
    }
}
