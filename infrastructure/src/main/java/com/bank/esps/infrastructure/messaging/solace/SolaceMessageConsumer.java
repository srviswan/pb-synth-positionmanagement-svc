package com.bank.esps.infrastructure.messaging.solace;

import com.bank.esps.domain.messaging.MessageConsumer;
import com.bank.esps.domain.messaging.PartitionAwareMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Solace JMS implementation of MessageConsumer
 * Uses Spring JMS listeners for consuming messages from Solace topics/queues
 */
@Component("solaceMessageConsumer")
@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "solace", matchIfMissing = false)
public class SolaceMessageConsumer implements MessageConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(SolaceMessageConsumer.class);
    
    private final Map<String, Consumer<String>> handlers = new ConcurrentHashMap<>();
    private final Map<String, Consumer<PartitionAwareMessage>> partitionAwareHandlers = new ConcurrentHashMap<>();
    private final JmsListenerEndpointRegistry registry;
    private final Map<String, Set<String>> partitionKeys = new ConcurrentHashMap<>(); // Track partition keys per topic
    
    @Value("${app.solace.default-destination-type:topic}")
    private String defaultDestinationType;
    
    @Autowired(required = false)
    public SolaceMessageConsumer(JmsListenerEndpointRegistry registry) {
        this.registry = registry;
        if (registry == null) {
            log.warn("SolaceMessageConsumer initialized without JMS Listener Registry. " +
                    "Ensure app.messaging.provider=solace and Solace is configured.");
        } else {
            log.info("SolaceMessageConsumer initialized with JMS Listener Registry");
        }
    }
    
    @Override
    public void subscribe(String topic, Consumer<String> messageHandler) {
        handlers.put(topic, messageHandler);
        log.info("Registered handler for Solace topic/queue: {}. Use @JmsListener annotation for automatic consumption.", topic);
        // Note: In Solace/JMS, subscriptions are typically done via @JmsListener annotations
        // This method registers the handler, but actual consumption happens via @JmsListener methods
    }
    
    @Override
    public void subscribePartitionAware(String topic, Consumer<PartitionAwareMessage> partitionAwareHandler) {
        partitionAwareHandlers.put(topic, partitionAwareHandler);
        log.info("Registered partition-aware handler for Solace topic/queue: {}", topic);
    }
    
    @Override
    public void unsubscribe(String topic) {
        handlers.remove(topic);
        partitionAwareHandlers.remove(topic);
        partitionKeys.remove(topic);
        
        // Stop the listener container if it exists
        if (registry != null) {
            MessageListenerContainer container = registry.getListenerContainer(topic);
            if (container != null) {
                container.stop();
                log.info("Stopped Solace listener container for topic: {}", topic);
            }
        }
        
        log.info("Unsubscribed from Solace topic/queue: {}", topic);
    }
    
    /**
     * Process a message received from Solace with partition awareness
     * This method extracts partition information from JMS properties
     */
    public void processMessageWithPartition(String topic, Message message) {
        try {
            if (!(message instanceof TextMessage)) {
                log.warn("Received non-text message from topic: {}", topic);
                return;
            }
            
            TextMessage textMessage = (TextMessage) message;
            String messageBody = textMessage.getText();
            
            // Extract message key
            String messageKey = textMessage.getStringProperty("messageKey");
            if (messageKey == null) {
                messageKey = textMessage.getJMSCorrelationID();
            }
            
            // Extract partition key (JMSXGroupID is standard for partition keys in JMS)
            String partitionKey = textMessage.getStringProperty("JMSXGroupID");
            if (partitionKey == null) {
                // Fallback to Solace-specific partition key property
                partitionKey = textMessage.getStringProperty("Solace_Partition_Key");
            }
            if (partitionKey == null) {
                // Use message key as partition key if available
                partitionKey = messageKey;
            }
            
            // Extract partition ID if available (Solace partition-aware queues)
            Integer partitionId = null;
            try {
                String partitionIdStr = textMessage.getStringProperty("Solace_Partition_ID");
                if (partitionIdStr != null) {
                    partitionId = Integer.parseInt(partitionIdStr);
                }
            } catch (Exception e) {
                // Partition ID not available or not a number
            }
            
            // Extract timestamp
            Long timestamp = null;
            try {
                timestamp = textMessage.getJMSTimestamp();
            } catch (Exception e) {
                timestamp = System.currentTimeMillis();
            }
            
            // Track partition keys for this topic
            if (partitionKey != null) {
                partitionKeys.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet())
                    .add(partitionKey);
            }
            
            // Create partition-aware message
            PartitionAwareMessage partitionAwareMessage = new PartitionAwareMessage(
                messageBody,
                messageKey,
                partitionId,
                partitionKey,
                topic,
                null, // Offset not applicable for Solace
                timestamp
            );
            
            log.debug("Received message from Solace topic: {}, key: {}, partitionKey: {}, partitionId: {}", 
                topic, messageKey, partitionKey, partitionId);
            
            // Try partition-aware handler first
            Consumer<PartitionAwareMessage> partitionAwareHandler = partitionAwareHandlers.get(topic);
            if (partitionAwareHandler != null) {
                partitionAwareHandler.accept(partitionAwareMessage);
                return;
            }
            
            // Fallback to simple handler
            Consumer<String> handler = handlers.get(topic);
            if (handler != null) {
                handler.accept(messageBody);
            } else {
                log.warn("No handler registered for Solace topic: {}. Message will be ignored.", topic);
            }
            
        } catch (JMSException e) {
            log.error("Failed to process message from Solace topic: {}", topic, e);
            throw new RuntimeException("Failed to process Solace message", e);
        }
    }
    
    /**
     * Process a message received from Solace (simple, backward compatible)
     * This method is called by @JmsListener annotated methods
     */
    public void processMessage(String topic, Message message) {
        // Delegate to partition-aware method
        processMessageWithPartition(topic, message);
    }
    
    /**
     * Get handler for a topic
     */
    public Consumer<String> getHandler(String topic) {
        return handlers.get(topic);
    }
    
    @Override
    public List<Integer> getAssignedPartitions(String topic) {
        // For Solace, partition information is not directly available via JMS
        // Would need to query Solace admin API or use partition-aware queue configuration
        // For now, return null to indicate partition info not available
        return null;
    }
    
    /**
     * Get partition keys seen for a topic
     */
    public Set<String> getPartitionKeys(String topic) {
        return partitionKeys.getOrDefault(topic, Collections.emptySet());
    }
}
