package com.bank.esps.domain.messaging;

import com.bank.esps.domain.auth.UserContext;

/**
 * Abstraction for message producers.
 * Allows switching between Kafka, Solace, or other messaging implementations.
 * Supports authorization via user context.
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
     * Send a message with user context for authorization
     * @param topic The topic/queue name
     * @param key The message key (for partitioning/ordering)
     * @param message The message payload
     * @param userContext User context for authorization and audit
     */
    default void send(String topic, String key, Object message, UserContext userContext) {
        // Default implementation: ignore user context (for backward compatibility)
        send(topic, key, message);
    }
    
    /**
     * Send a message without a key
     * @param topic The topic/queue name
     * @param message The message payload
     */
    default void send(String topic, Object message) {
        send(topic, null, message);
    }
}
