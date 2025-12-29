package com.bank.esps.infrastructure.messaging.kafka;

import com.bank.esps.domain.messaging.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka implementation of MessageProducer
 */
@Component("kafkaMessageProducer")
public class KafkaMessageProducer implements MessageProducer {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaMessageProducer.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public KafkaMessageProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    @Override
    public void send(String topic, String key, Object message) {
        try {
            if (key != null) {
                kafkaTemplate.send(topic, key, message);
            } else {
                kafkaTemplate.send(topic, message);
            }
            log.debug("Sent message to topic: {}, key: {}", topic, key);
        } catch (Exception e) {
            log.error("Failed to send message to topic: {}", topic, e);
            throw new RuntimeException("Failed to send message to Kafka", e);
        }
    }
}
