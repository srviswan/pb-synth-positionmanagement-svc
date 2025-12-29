package com.bank.esps.infrastructure.messaging.solace;

import com.bank.esps.domain.messaging.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Solace implementation of MessageProducer
 * Note: Requires Solace JMS dependencies to be added when using Solace
 */
@Component("solaceMessageProducer")
public class SolaceMessageProducer implements MessageProducer {
    
    private static final Logger log = LoggerFactory.getLogger(SolaceMessageProducer.class);
    
    public SolaceMessageProducer() {
        log.warn("SolaceMessageProducer initialized but Solace dependencies not available. Add solace-jms-spring-boot-starter to use Solace.");
    }
    
    @Override
    public void send(String topic, String key, Object message) {
        log.warn("Solace not configured. Message not sent to topic: {}, key: {}", topic, key);
        throw new UnsupportedOperationException("Solace messaging not configured. Add solace-jms-spring-boot-starter dependency.");
    }
}
