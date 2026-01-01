package com.bank.esps.infrastructure.messaging.solace;

import com.bank.esps.domain.messaging.MessageProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

/**
 * Solace JMS implementation of MessageProducer
 * Uses Spring JMS Template for sending messages to Solace topics/queues
 */
@Component("solaceMessageProducer")
@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "solace", matchIfMissing = false)
public class SolaceMessageProducer implements MessageProducer {
    
    // Note: This bean is only created when app.messaging.provider=solace
    // When Kafka is used, this bean won't be created, avoiding dependency issues
    
    private static final Logger log = LoggerFactory.getLogger(SolaceMessageProducer.class);
    
    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${app.solace.default-destination-type:topic}")
    private String defaultDestinationType;
    
    @Autowired(required = false)
    public SolaceMessageProducer(
            @Qualifier("solaceJmsTemplate") JmsTemplate solaceJmsTemplate,
            ObjectMapper objectMapper) {
        if (solaceJmsTemplate == null) {
            log.warn("SolaceMessageProducer initialized without JMS Template. " +
                    "Ensure app.messaging.provider=solace and Solace is configured.");
        }
        this.jmsTemplate = solaceJmsTemplate;
        this.objectMapper = objectMapper;
        log.info("SolaceMessageProducer initialized with JMS Template");
    }
    
    @Override
    public void send(String topic, String key, Object message) {
        if (jmsTemplate == null) {
            log.error("Solace JMS Template not available. Cannot send message to topic: {}", topic);
            throw new UnsupportedOperationException("Solace messaging not configured. Set app.messaging.provider=solace and configure Solace connection.");
        }
        
        try {
            String messageBody;
            if (message instanceof String) {
                messageBody = (String) message;
            } else {
                messageBody = objectMapper.writeValueAsString(message);
            }
            
            // Determine destination type (topic or queue)
            boolean isQueue = topic.endsWith("-queue") || topic.contains("/queue/");
            String destination = isQueue ? topic : topic;
            
            // Send message with key as JMS property
            jmsTemplate.send(destination, (Session session) -> {
                TextMessage textMessage = session.createTextMessage(messageBody);
                
                // Set message key as JMS property for partitioning/ordering
                if (key != null && !key.isEmpty()) {
                    textMessage.setStringProperty("messageKey", key);
                    textMessage.setStringProperty("JMSCorrelationID", key);
                }
                
                // Set additional properties
                textMessage.setStringProperty("contentType", "application/json");
                textMessage.setStringProperty("topic", topic);
                
                return textMessage;
            });
            
            log.debug("Sent message to Solace topic/queue: {}, key: {}", topic, key);
            
        } catch (Exception e) {
            log.error("Failed to send message to Solace topic: {}", topic, e);
            throw new RuntimeException("Failed to send message to Solace", e);
        }
    }
    
    /**
     * Send message to a specific queue
     */
    public void sendToQueue(String queueName, String key, Object message) {
        if (jmsTemplate == null) {
            log.error("Solace JMS Template not available. Cannot send message to queue: {}", queueName);
            throw new UnsupportedOperationException("Solace messaging not configured.");
        }
        
        try {
            String messageBody;
            if (message instanceof String) {
                messageBody = (String) message;
            } else {
                messageBody = objectMapper.writeValueAsString(message);
            }
            
            // Send to queue (Solace uses queue:// prefix for queues)
            String queueDestination = queueName.startsWith("queue://") ? queueName : "queue://" + queueName;
            
            jmsTemplate.send(queueDestination, (Session session) -> {
                TextMessage textMessage = session.createTextMessage(messageBody);
                
                if (key != null && !key.isEmpty()) {
                    textMessage.setStringProperty("messageKey", key);
                    textMessage.setStringProperty("JMSCorrelationID", key);
                }
                
                textMessage.setStringProperty("contentType", "application/json");
                textMessage.setStringProperty("queue", queueName);
                
                return textMessage;
            });
            
            log.debug("Sent message to Solace queue: {}, key: {}", queueName, key);
            
        } catch (Exception e) {
            log.error("Failed to send message to Solace queue: {}", queueName, e);
            throw new RuntimeException("Failed to send message to Solace queue", e);
        }
    }
}
