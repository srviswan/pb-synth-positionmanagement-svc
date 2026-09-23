package com.bank.esps.domain.messaging;

import java.util.function.Consumer;

/**
 * Abstraction for message consumers.
 * Allows switching between Kafka, Solace, or other messaging implementations.
 * Supports both simple and partition-aware message consumption.
 */
public interface MessageConsumer {
    
    /**
     * Subscribe to a topic/queue and process messages
     * @param topic The topic/queue name
     * @param messageHandler The handler function to process messages
     */
    void subscribe(String topic, Consumer<String> messageHandler);
    
    /**
     * Subscribe to a topic/queue with partition-aware message processing
     * @param topic The topic/queue name
     * @param partitionAwareHandler The handler function that receives partition-aware messages
     */
    default void subscribePartitionAware(String topic, Consumer<PartitionAwareMessage> partitionAwareHandler) {
        // Default implementation: wrap partition-aware handler with simple handler
        subscribe(topic, messageBody -> {
            // Create a basic partition-aware message without partition info
            PartitionAwareMessage message = new PartitionAwareMessage(
                messageBody, null, null, null, topic, null, System.currentTimeMillis()
            );
            partitionAwareHandler.accept(message);
        });
    }
    
    /**
     * Unsubscribe from a topic/queue
     * @param topic The topic/queue name
     */
    void unsubscribe(String topic);
    
    /**
     * Get partition information for a topic (if available)
     * @param topic The topic/queue name
     * @return Number of partitions, or null if not available/not applicable
     */
    default Integer getPartitionCount(String topic) {
        return null; // Default: unknown
    }
    
    /**
     * Get assigned partitions for a topic (if available)
     * @param topic The topic/queue name
     * @return List of partition IDs assigned to this consumer, or null if not available
     */
    default java.util.List<Integer> getAssignedPartitions(String topic) {
        return null; // Default: unknown
    }
}
