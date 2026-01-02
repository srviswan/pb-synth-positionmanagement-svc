package com.bank.esps.infrastructure.messaging.kafka;

import com.bank.esps.domain.auth.AuthorizationService;
import com.bank.esps.domain.auth.UserContext;
import com.bank.esps.domain.messaging.MessageProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Kafka implementation of MessageProducer with authorization support
 * Based on messaging_entitlements_integration.md
 */
@Component("kafkaMessageProducer")
public class KafkaMessageProducer implements MessageProducer {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaMessageProducer.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Optional<AuthorizationService> authorizationService;
    
    public KafkaMessageProducer(KafkaTemplate<String, Object> kafkaTemplate,
                               java.util.Optional<AuthorizationService> authorizationService) {
        this.kafkaTemplate = kafkaTemplate;
        this.authorizationService = authorizationService;
    }
    
    @Override
    public void send(String topic, String key, Object message) {
        send(topic, key, message, null);
    }
    
    /**
     * Send message with optional user context for authorization
     */
    public void send(String topic, String key, Object message, UserContext userContext) {
        try {
            ProducerRecord<String, Object> record;
            
            if (key != null) {
                record = new ProducerRecord<>(topic, key, message);
            } else {
                record = new ProducerRecord<>(topic, message);
            }
            
            // Add user context to headers if available
            if (userContext != null) {
                record.headers().add("user-id", userContext.getUserId().getBytes());
                if (userContext.getRoles() != null && !userContext.getRoles().isEmpty()) {
                    record.headers().add("user-roles", 
                        String.join(",", userContext.getRoles()).getBytes());
                }
                if (userContext.getAccountIds() != null && !userContext.getAccountIds().isEmpty()) {
                    record.headers().add("user-accounts", 
                        String.join(",", userContext.getAccountIds()).getBytes());
                }
            }
            
            kafkaTemplate.send(record);
            log.debug("Sent message to topic: {}, key: {}, userId: {}", 
                topic, key, userContext != null ? userContext.getUserId() : "anonymous");
        } catch (Exception e) {
            log.error("Failed to send message to topic: {}", topic, e);
            throw new RuntimeException("Failed to send message to Kafka", e);
        }
    }
}
