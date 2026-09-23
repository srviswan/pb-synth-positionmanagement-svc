package com.bank.esps.infrastructure.messaging.kafka;

import com.bank.esps.domain.messaging.MessageConsumer;
import com.bank.esps.domain.messaging.PartitionAwareMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Kafka implementation of MessageConsumer with partition awareness
 */
@Component("kafkaMessageConsumer")
public class KafkaMessageConsumer implements MessageConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaMessageConsumer.class);
    
    private final Map<String, Consumer<String>> handlers = new ConcurrentHashMap<>();
    private final Map<String, Consumer<PartitionAwareMessage>> partitionAwareHandlers = new ConcurrentHashMap<>();
    private final KafkaListenerEndpointRegistry registry;
    private final Map<String, List<Integer>> assignedPartitions = new ConcurrentHashMap<>();
    
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
    public void subscribePartitionAware(String topic, Consumer<PartitionAwareMessage> partitionAwareHandler) {
        partitionAwareHandlers.put(topic, partitionAwareHandler);
        log.info("Subscribed to Kafka topic with partition awareness: {}", topic);
    }
    
    @Override
    public void unsubscribe(String topic) {
        handlers.remove(topic);
        partitionAwareHandlers.remove(topic);
        MessageListenerContainer container = registry.getListenerContainer(topic);
        if (container != null) {
            container.stop();
        }
        log.info("Unsubscribed from Kafka topic: {}", topic);
    }
    
    @Override
    public List<Integer> getAssignedPartitions(String topic) {
        return assignedPartitions.getOrDefault(topic, Collections.emptyList());
    }
    
    /**
     * Process a message from Kafka with partition information
     * This method can be called by @KafkaListener methods that want partition awareness
     */
    public void processMessageWithPartition(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        int partition = record.partition();
        String key = record.key();
        String value = record.value();
        long offset = record.offset();
        long timestamp = record.timestamp();
        
        // Update assigned partitions tracking
        assignedPartitions.computeIfAbsent(topic, k -> new ArrayList<>())
            .add(partition);
        
        // Create partition-aware message
        PartitionAwareMessage partitionAwareMessage = new PartitionAwareMessage(
            value,
            key,
            partition,
            key, // Partition key is same as message key in Kafka
            topic,
            offset,
            timestamp
        );
        
        // Try partition-aware handler first
        Consumer<PartitionAwareMessage> partitionAwareHandler = partitionAwareHandlers.get(topic);
        if (partitionAwareHandler != null) {
            log.debug("Processing message from Kafka topic: {}, partition: {}, offset: {}", 
                topic, partition, offset);
            partitionAwareHandler.accept(partitionAwareMessage);
            return;
        }
        
        // Fallback to simple handler
        Consumer<String> handler = handlers.get(topic);
        if (handler != null) {
            handler.accept(value);
        } else {
            log.warn("No handler registered for topic: {}", topic);
        }
    }
    
    /**
     * Process a message from Kafka (simple, backward compatible)
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
