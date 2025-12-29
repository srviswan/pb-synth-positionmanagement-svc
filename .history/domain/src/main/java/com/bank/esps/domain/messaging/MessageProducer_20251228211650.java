package com.bank.esps.domain.messaging;

/**
 * Abstraction for message producers.
 * Allows switching between Kafka, Solace, or other messaging implementations.
 */
public interface MessageProducer {
    
    /**
     * Send a message to a topic/queue
     * @param topic The topic/queue name
     * @param key The message key (for partitioning/ordering)
     * @param message The message payload
     */
    void send(String topic, String key, Object message);
    
    /**
     * Send a message without a key
     * @param topic The topic/queue name
     * @param message The message payload
     */
    default void send(String topic, Object message) {
        send(topic, null, message);
    }
}
