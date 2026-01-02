package com.bank.esps.infrastructure.messaging.kafka;

import com.bank.esps.domain.messaging.PartitionAwareMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases to verify KafkaMessageConsumer correctly extracts partition information
 */
@ExtendWith(MockitoExtension.class)
class KafkaMessageConsumerPartitionTest {

    @Mock
    private KafkaListenerEndpointRegistry registry;

    private KafkaMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new KafkaMessageConsumer(registry);
    }

    @Test
    void testProcessMessageWithPartition_ExtractsPartitionInfo() {
        // Given
        String topic = "test-topic";
        int partition = 3;
        String key = "position-key-123";
        String value = "{\"tradeId\":\"T001\"}";
        long offset = 100L;
        long timestamp = System.currentTimeMillis();

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            topic, partition, offset, timestamp, null, null, 0, 0, key, value, null, null
        );

        AtomicReference<PartitionAwareMessage> capturedMessage = new AtomicReference<>();
        Consumer<PartitionAwareMessage> handler = capturedMessage::set;

        consumer.subscribePartitionAware(topic, handler);

        // When
        consumer.processMessageWithPartition(record);

        // Then
        assertNotNull(capturedMessage.get());
        PartitionAwareMessage message = capturedMessage.get();
        assertEquals(value, message.getMessageBody());
        assertEquals(key, message.getMessageKey());
        assertEquals(partition, message.getPartitionId());
        assertEquals(key, message.getPartitionKey());
        assertEquals(topic, message.getTopic());
        assertEquals(offset, message.getOffset());
        assertEquals(timestamp, message.getTimestamp());
    }

    @Test
    void testProcessMessageWithPartition_TracksAssignedPartitions() {
        // Given
        String topic = "test-topic";
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        
        // Create records from different partitions
        for (int i = 0; i < 5; i++) {
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                topic, i, 100L + i, System.currentTimeMillis(), 
                null, null, 0, 0, "key-" + i, "value-" + i, null, null
            );
            records.add(record);
        }

        Consumer<PartitionAwareMessage> handler = msg -> {};

        consumer.subscribePartitionAware(topic, handler);

        // When
        for (ConsumerRecord<String, String> record : records) {
            consumer.processMessageWithPartition(record);
        }

        // Then
        List<Integer> assignedPartitions = consumer.getAssignedPartitions(topic);
        assertNotNull(assignedPartitions);
        assertEquals(5, assignedPartitions.size());
        assertTrue(assignedPartitions.contains(0));
        assertTrue(assignedPartitions.contains(1));
        assertTrue(assignedPartitions.contains(2));
        assertTrue(assignedPartitions.contains(3));
        assertTrue(assignedPartitions.contains(4));
    }

    @Test
    void testProcessMessageWithPartition_SameKeySamePartition() {
        // Given
        String topic = "test-topic";
        String key = "position-key-456";
        int partition = 2;
        
        ConsumerRecord<String, String> record1 = new ConsumerRecord<>(
            topic, partition, 100L, System.currentTimeMillis(),
            null, null, 0, 0, key, "value1", null, null
        );
        ConsumerRecord<String, String> record2 = new ConsumerRecord<>(
            topic, partition, 101L, System.currentTimeMillis(),
            null, null, 0, 0, key, "value2", null, null
        );

        List<PartitionAwareMessage> messages = new ArrayList<>();
        Consumer<PartitionAwareMessage> handler = messages::add;

        consumer.subscribePartitionAware(topic, handler);

        // When
        consumer.processMessageWithPartition(record1);
        consumer.processMessageWithPartition(record2);

        // Then
        assertEquals(2, messages.size());
        assertEquals(partition, messages.get(0).getPartitionId());
        assertEquals(partition, messages.get(1).getPartitionId());
        assertEquals(key, messages.get(0).getPartitionKey());
        assertEquals(key, messages.get(1).getPartitionKey());
    }

    @Test
    void testProcessMessageWithPartition_DifferentKeysDifferentPartitions() {
        // Given
        String topic = "test-topic";
        String key1 = "position-key-111";
        String key2 = "position-key-222";
        int partition1 = 1;
        int partition2 = 5;
        
        ConsumerRecord<String, String> record1 = new ConsumerRecord<>(
            topic, partition1, 100L, System.currentTimeMillis(),
            null, null, 0, 0, key1, "value1", null, null
        );
        ConsumerRecord<String, String> record2 = new ConsumerRecord<>(
            topic, partition2, 101L, System.currentTimeMillis(),
            null, null, 0, 0, key2, "value2", null, null
        );

        List<PartitionAwareMessage> messages = new ArrayList<>();
        Consumer<PartitionAwareMessage> handler = messages::add;

        consumer.subscribePartitionAware(topic, handler);

        // When
        consumer.processMessageWithPartition(record1);
        consumer.processMessageWithPartition(record2);

        // Then
        assertEquals(2, messages.size());
        assertNotEquals(messages.get(0).getPartitionId(), messages.get(1).getPartitionId());
        assertNotEquals(messages.get(0).getPartitionKey(), messages.get(1).getPartitionKey());
    }

    @Test
    void testProcessMessageWithPartition_FallbackToSimpleHandler() {
        // Given
        String topic = "test-topic";
        String value = "{\"tradeId\":\"T001\"}";
        
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            topic, 0, 100L, System.currentTimeMillis(),
            null, null, 0, 0, "key", value, null, null
        );

        AtomicReference<String> capturedValue = new AtomicReference<>();
        Consumer<String> simpleHandler = capturedValue::set;

        consumer.subscribe(topic, simpleHandler);

        // When
        consumer.processMessageWithPartition(record);

        // Then
        assertEquals(value, capturedValue.get());
    }

    @Test
    void testProcessMessageWithPartition_NullKeyHandled() {
        // Given
        String topic = "test-topic";
        String value = "{\"tradeId\":\"T001\"}";
        
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            topic, 0, 100L, System.currentTimeMillis(),
            null, null, 0, 0, null, value, null, null
        );

        AtomicReference<PartitionAwareMessage> capturedMessage = new AtomicReference<>();
        Consumer<PartitionAwareMessage> handler = capturedMessage::set;

        consumer.subscribePartitionAware(topic, handler);

        // When
        consumer.processMessageWithPartition(record);

        // Then
        assertNotNull(capturedMessage.get());
        PartitionAwareMessage message = capturedMessage.get();
        assertNull(message.getMessageKey());
        assertNull(message.getPartitionKey());
        assertEquals(0, message.getPartitionId());
    }
}
