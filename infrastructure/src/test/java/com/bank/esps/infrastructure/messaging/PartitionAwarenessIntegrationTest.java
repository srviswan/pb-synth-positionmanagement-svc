package com.bank.esps.infrastructure.messaging;

import com.bank.esps.domain.messaging.PartitionAwareMessage;
import com.bank.esps.infrastructure.messaging.kafka.KafkaMessageConsumer;
import com.bank.esps.infrastructure.messaging.kafka.KafkaMessageProducer;
import com.bank.esps.infrastructure.messaging.solace.SolaceMessageConsumer;
import com.bank.esps.infrastructure.messaging.solace.SolaceMessageProducer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;

import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests to verify partition awareness works correctly end-to-end
 * Tests that messages with the same key are routed to the same partition
 */
@ExtendWith(MockitoExtension.class)
class PartitionAwarenessIntegrationTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private KafkaListenerEndpointRegistry registry;

    @Mock
    private Session session;

    @Mock
    private TextMessage textMessage;

    private KafkaMessageProducer kafkaProducer;
    private KafkaMessageConsumer kafkaConsumer;
    private SolaceMessageProducer solaceProducer;
    private SolaceMessageConsumer solaceConsumer;

    @BeforeEach
    void setUp() throws JMSException {
        kafkaProducer = new KafkaMessageProducer(kafkaTemplate, Optional.empty());
        kafkaConsumer = new KafkaMessageConsumer(registry);
        
        solaceProducer = new SolaceMessageProducer(jmsTemplate, new com.fasterxml.jackson.databind.ObjectMapper());
        solaceConsumer = new SolaceMessageConsumer();
        
        when(session.createTextMessage(anyString())).thenReturn(textMessage);
    }

    @Test
    void testKafka_SameKeySamePartition() {
        // Given
        String topic = "test-topic";
        String key = "position-key-123";
        String message1 = "{\"tradeId\":\"T001\"}";
        String message2 = "{\"tradeId\":\"T002\"}";
        String message3 = "{\"tradeId\":\"T003\"}";

        // When - Send multiple messages with same key
        kafkaProducer.send(topic, key, message1);
        kafkaProducer.send(topic, key, message2);
        kafkaProducer.send(topic, key, message3);

        // Then - Verify all messages have the same key
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(3)).send(recordCaptor.capture());
        
        var records = recordCaptor.getAllValues();
        assertEquals(3, records.size());
        
        // All records should have the same key (Kafka will route to same partition)
        Set<String> keys = new HashSet<>();
        for (ProducerRecord<String, Object> record : records) {
            keys.add(record.key());
        }
        assertEquals(1, keys.size());
        assertTrue(keys.contains(key));
    }

    @Test
    void testKafka_DifferentKeysDifferentPartitions() {
        // Given
        String topic = "test-topic";
        String[] keys = {"key-1", "key-2", "key-3", "key-4", "key-5"};
        String message = "{\"tradeId\":\"T001\"}";

        // When - Send messages with different keys
        for (String key : keys) {
            kafkaProducer.send(topic, key, message);
        }

        // Then - Verify all messages have different keys
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(keys.length)).send(recordCaptor.capture());
        
        var records = recordCaptor.getAllValues();
        Set<String> uniqueKeys = new HashSet<>();
        for (ProducerRecord<String, Object> record : records) {
            uniqueKeys.add(record.key());
        }
        assertEquals(keys.length, uniqueKeys.size());
    }

    @Test
    void testKafka_ConsumerExtractsPartitionInfo() {
        // Given
        String topic = "test-topic";
        String key = "position-key-456";
        int partition = 2;
        long offset = 100L;
        long timestamp = System.currentTimeMillis();
        String value = "{\"tradeId\":\"T001\"}";

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            topic, partition, offset, timestamp, null, null, 0, 0, key, value, null, null
        );

        List<PartitionAwareMessage> messages = new ArrayList<>();
        Consumer<PartitionAwareMessage> handler = messages::add;

        kafkaConsumer.subscribePartitionAware(topic, handler);

        // When
        kafkaConsumer.processMessageWithPartition(record);

        // Then
        assertEquals(1, messages.size());
        PartitionAwareMessage message = messages.get(0);
        assertEquals(key, message.getPartitionKey());
        assertEquals(partition, message.getPartitionId());
        assertEquals(offset, message.getOffset());
    }

    @Test
    void testSolace_SameKeySamePartitionKey() throws JMSException {
        // Given
        String topic = "test-topic";
        String key = "position-key-789";
        String message1 = "{\"tradeId\":\"T001\"}";
        String message2 = "{\"tradeId\":\"T002\"}";
        String message3 = "{\"tradeId\":\"T003\"}";

        // When - Send multiple messages with same key
        solaceProducer.send(topic, key, message1);
        solaceProducer.send(topic, key, message2);
        solaceProducer.send(topic, key, message3);

        // Then - Verify all messages set the same partition key
        ArgumentCaptor<org.springframework.jms.core.MessageCreator> creatorCaptor = 
            ArgumentCaptor.forClass(org.springframework.jms.core.MessageCreator.class);
        verify(jmsTemplate, times(3)).send(eq(topic), creatorCaptor.capture());
        
        var creators = creatorCaptor.getAllValues();
        assertEquals(3, creators.size());
        
        // Verify each message sets JMSXGroupID to the same key
        for (org.springframework.jms.core.MessageCreator creator : creators) {
            reset(textMessage);
            when(session.createTextMessage(anyString())).thenReturn(textMessage);
            creator.createMessage(session);
            verify(textMessage).setStringProperty("JMSXGroupID", key);
            verify(textMessage).setStringProperty("Solace_Partition_Key", key);
        }
    }

    @Test
    void testSolace_ConsumerExtractsPartitionInfo() throws JMSException {
        // Given
        String topic = "test-topic";
        String partitionKey = "position-key-999";
        String messageBody = "{\"tradeId\":\"T001\"}";
        long timestamp = System.currentTimeMillis();

        when(textMessage.getStringProperty("JMSXGroupID")).thenReturn(partitionKey);
        when(textMessage.getStringProperty("messageKey")).thenReturn(partitionKey);
        when(textMessage.getJMSTimestamp()).thenReturn(timestamp);
        when(textMessage.getText()).thenReturn(messageBody);

        List<PartitionAwareMessage> messages = new ArrayList<>();
        Consumer<PartitionAwareMessage> handler = messages::add;

        solaceConsumer.subscribePartitionAware(topic, handler);

        // When
        solaceConsumer.processMessageWithPartition(topic, textMessage);

        // Then
        assertEquals(1, messages.size());
        PartitionAwareMessage message = messages.get(0);
        assertEquals(partitionKey, message.getPartitionKey());
        assertEquals(partitionKey, message.getMessageKey());
        assertEquals(timestamp, message.getTimestamp());
    }

    @Test
    void testPartitionConsistency_MultipleMessagesSameKey() {
        // Given
        String topic = "test-topic";
        String key = "position-key-consistent";
        int partition = 3;
        int messageCount = 10;

        List<PartitionAwareMessage> messages = new ArrayList<>();
        Consumer<PartitionAwareMessage> handler = messages::add;

        kafkaConsumer.subscribePartitionAware(topic, handler);

        // When - Process multiple messages with same key from same partition
        for (int i = 0; i < messageCount; i++) {
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                topic, partition, 100L + i, System.currentTimeMillis(),
                null, null, 0, 0, key, "message-" + i, null, null
            );
            kafkaConsumer.processMessageWithPartition(record);
        }

        // Then - All messages should have the same partition key and partition ID
        assertEquals(messageCount, messages.size());
        Set<String> partitionKeys = new HashSet<>();
        Set<Integer> partitionIds = new HashSet<>();
        
        for (PartitionAwareMessage msg : messages) {
            partitionKeys.add(msg.getPartitionKey());
            partitionIds.add(msg.getPartitionId());
        }
        
        assertEquals(1, partitionKeys.size());
        assertTrue(partitionKeys.contains(key));
        assertEquals(1, partitionIds.size());
        assertTrue(partitionIds.contains(partition));
    }

    @Test
    void testPartitionDistribution_MultipleKeys() {
        // Given
        String topic = "test-topic";
        String[] keys = {"key-A", "key-B", "key-C", "key-D", "key-E"};
        int messageCount = 5;

        Map<String, List<PartitionAwareMessage>> messagesByKey = new HashMap<>();
        Consumer<PartitionAwareMessage> handler = msg -> {
            messagesByKey.computeIfAbsent(msg.getPartitionKey(), k -> new ArrayList<>()).add(msg);
        };

        kafkaConsumer.subscribePartitionAware(topic, handler);

        // When - Process messages with different keys
        for (int i = 0; i < messageCount; i++) {
            String key = keys[i];
            int partition = i % 3; // Distribute across 3 partitions
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                topic, partition, 100L + i, System.currentTimeMillis(),
                null, null, 0, 0, key, "message-" + i, null, null
            );
            kafkaConsumer.processMessageWithPartition(record);
        }

        // Then - Messages should be grouped by partition key
        assertEquals(keys.length, messagesByKey.size());
        for (String key : keys) {
            assertTrue(messagesByKey.containsKey(key));
            assertEquals(1, messagesByKey.get(key).size());
        }
    }
}
