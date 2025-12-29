package com.bank.esps.infrastructure.messaging.solace;

import com.bank.esps.domain.messaging.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Solace implementation of MessageProducer
 */
@Component("solaceMessageProducer")
public class SolaceMessageProducer implements MessageProducer {
    
    private static final Logger log = LoggerFactory.getLogger(SolaceMessageProducer.class);
    
    private final JmsTemplate jmsTemplate;
    
    public SolaceMessageProducer(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }
    
    @Override
    public void send(String topic, String key, Object message) {
        try {
            jmsTemplate.convertAndSend(topic, message);
            log.debug("Sent message to Solace topic: {}, key: {}", topic, key);
        } catch (Exception e) {
            log.error("Failed to send message to Solace topic: {}", topic, e);
            throw new RuntimeException("Failed to send message to Solace", e);
        }
    }
}
