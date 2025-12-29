package com.bank.esps.infrastructure.messaging.kafka;

import com.bank.esps.domain.messaging.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Kafka implementation of MessageConsumer
 */
@Component("kafkaMessageConsumer")
public class KafkaMessageConsumer implements MessageConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaMessageConsumer.class);
    
    private final Map<String, Consumer<String>> handlers = new ConcurrentHashMap<>();
    private final KafkaListenerEndpointRegistry registry;
    
    public KafkaMessageConsumer(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }
    
    @Override
    public void subscribe(String topic, Consumer<String> messageHandler) {
        handlers.put(topic, messageHandler);
        log.info("Subscribed to Kafka topic: {}", topic);
        // Note: In a real implementation, you'd dynamically create listeners
        // For now, we'll use @KafkaListener annotations on specific methods
    }
    
    @Override
    public void unsubscribe(String topic) {
        handlers.remove(topic);
        MessageListenerContainer container = registry.getListenerContainer(topic);
        if (container != null) {
            container.stop();
        }
        log.info("Unsubscribed from Kafka topic: {}", topic);
    }
    
    /**
     * Process a message from Kafka
     */
    public void processMessage(String topic, String message) {
        Consumer<String> handler = handlers.get(topic);
        if (handler != null) {
            handler.accept(message);
        } else {
            log.warn("No handler registered for topic: {}", topic);
        }
    }
}
