package com.bank.esps.infrastructure.messaging.solace;

import com.bank.esps.domain.messaging.MessageConsumer;
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
import java.util.Map;
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
    private final JmsListenerEndpointRegistry registry;
    
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
    public void unsubscribe(String topic) {
        handlers.remove(topic);
        
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
     * Process a message received from Solace
     * This method is called by @JmsListener annotated methods
     */
    public void processMessage(String topic, Message message) {
        try {
            if (!(message instanceof TextMessage)) {
                log.warn("Received non-text message from topic: {}", topic);
                return;
            }
            
            TextMessage textMessage = (TextMessage) message;
            String messageBody = textMessage.getText();
            
            // Extract message key if available
            String messageKey = textMessage.getStringProperty("messageKey");
            if (messageKey == null) {
                messageKey = textMessage.getJMSCorrelationID();
            }
            
            log.debug("Received message from Solace topic: {}, key: {}", topic, messageKey);
            
            // Get handler for this topic
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
     * Get handler for a topic
     */
    public Consumer<String> getHandler(String topic) {
        return handlers.get(topic);
    }
}
