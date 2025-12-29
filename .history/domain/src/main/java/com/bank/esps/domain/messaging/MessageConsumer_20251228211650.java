package com.bank.esps.domain.messaging;

import java.util.function.Consumer;

/**
 * Abstraction for message consumers.
 * Allows switching between Kafka, Solace, or other messaging implementations.
 */
public interface MessageConsumer {
    
    /**
     * Subscribe to a topic/queue and process messages
     * @param topic The topic/queue name
     * @param messageHandler The handler function to process messages
     */
    void subscribe(String topic, Consumer<String> messageHandler);
    
    /**
     * Unsubscribe from a topic/queue
     * @param topic The topic/queue name
     */
    void unsubscribe(String topic);
}
