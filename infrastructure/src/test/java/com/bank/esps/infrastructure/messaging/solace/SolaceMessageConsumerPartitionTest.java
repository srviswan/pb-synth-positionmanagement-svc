package com.bank.esps.infrastructure.messaging.solace;

import com.bank.esps.domain.messaging.PartitionAwareMessage;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases to verify SolaceMessageConsumer correctly extracts partition information
 */
@ExtendWith(MockitoExtension.class)
class SolaceMessageConsumerPartitionTest {

    @Mock
    private TextMessage textMessage;

    private SolaceMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SolaceMessageConsumer();
    }

    @Test
    void testExtractPartitionInfo_FromJMSXGroupID() throws JMSException {
        // Given
        String topic = "test-topic";
        String partitionKey = "position-key-123";
        String messageBody = "{\"tradeId\":\"T001\"}";
        long timestamp = System.currentTimeMillis();

        when(textMessage.getStringProperty("JMSXGroupID")).thenReturn(partitionKey);
        when(textMessage.getStringProperty("Solace_Partition_Key")).thenReturn(null);
        when(textMessage.getStringProperty("Solace_Partition_ID")).thenReturn(null);
        when(textMessage.getStringProperty("messageKey")).thenReturn(partitionKey);
        when(textMessage.getJMSTimestamp()).thenReturn(timestamp);
        when(textMessage.getText()).thenReturn(messageBody);

        AtomicReference<PartitionAwareMessage> capturedMessage = new AtomicReference<>();
        Consumer<PartitionAwareMessage> handler = capturedMessage::set;

        consumer.subscribePartitionAware(topic, handler);

        // When
        consumer.processMessageWithPartition(topic, textMessage);

        // Then
        assertNotNull(capturedMessage.get());
        PartitionAwareMessage message = capturedMessage.get();
        assertEquals(messageBody, message.getMessageBody());
        assertEquals(partitionKey, message.getMessageKey());
        assertEquals(partitionKey, message.getPartitionKey());
        assertEquals(topic, message.getTopic());
        assertEquals(timestamp, message.getTimestamp());
    }

    @Test
    void testExtractPartitionInfo_FromSolacePartitionKey() throws JMSException {
        // Given
        String topic = "test-topic";
        String partitionKey = "position-key-456";
        String messageBody = "{\"tradeId\":\"T002\"}";
        long timestamp = System.currentTimeMillis();

        when(textMessage.getStringProperty("JMSXGroupID")).thenReturn(null);
        when(textMessage.getStringProperty("Solace_Partition_Key")).thenReturn(partitionKey);
        when(textMessage.getStringProperty("Solace_Partition_ID")).thenReturn("2");
        when(textMessage.getStringProperty("messageKey")).thenReturn(partitionKey);
        when(textMessage.getJMSTimestamp()).thenReturn(timestamp);
        when(textMessage.getText()).thenReturn(messageBody);

        AtomicReference<PartitionAwareMessage> capturedMessage = new AtomicReference<>();
        Consumer<PartitionAwareMessage> handler = capturedMessage::set;

        consumer.subscribePartitionAware(topic, handler);

        // When
        consumer.processMessageWithPartition(topic, textMessage);

        // Then
        assertNotNull(capturedMessage.get());
        PartitionAwareMessage message = capturedMessage.get();
        assertEquals(partitionKey, message.getPartitionKey());
        assertEquals(Integer.valueOf(2), message.getPartitionId());
    }

    @Test
    void testExtractPartitionInfo_SameKeySamePartitionKey() throws JMSException {
        // Given
        String topic = "test-topic";
        String partitionKey = "position-key-789";
        String messageBody1 = "{\"tradeId\":\"T001\"}";
        String messageBody2 = "{\"tradeId\":\"T002\"}";
        long timestamp = System.currentTimeMillis();

        TextMessage message1 = mock(TextMessage.class);
        TextMessage message2 = mock(TextMessage.class);

        when(message1.getStringProperty("JMSXGroupID")).thenReturn(partitionKey);
        when(message1.getStringProperty("messageKey")).thenReturn(partitionKey);
        when(message1.getJMSTimestamp()).thenReturn(timestamp);
        when(message1.getText()).thenReturn(messageBody1);

        when(message2.getStringProperty("JMSXGroupID")).thenReturn(partitionKey);
        when(message2.getStringProperty("messageKey")).thenReturn(partitionKey);
        when(message2.getJMSTimestamp()).thenReturn(timestamp + 1000);
        when(message2.getText()).thenReturn(messageBody2);

        List<PartitionAwareMessage> messages = new ArrayList<>();
        Consumer<PartitionAwareMessage> handler = messages::add;

        consumer.subscribePartitionAware(topic, handler);

        // When
        consumer.processMessageWithPartition(topic, message1);
        consumer.processMessageWithPartition(topic, message2);

        // Then
        assertEquals(2, messages.size());
        assertEquals(partitionKey, messages.get(0).getPartitionKey());
        assertEquals(partitionKey, messages.get(1).getPartitionKey());
    }

    @Test
    void testExtractPartitionInfo_DifferentKeysDifferentPartitionKeys() throws JMSException {
        // Given
        String topic = "test-topic";
        String partitionKey1 = "position-key-111";
        String partitionKey2 = "position-key-222";
        String messageBody = "{\"tradeId\":\"T001\"}";
        long timestamp = System.currentTimeMillis();

        TextMessage message1 = mock(TextMessage.class);
        TextMessage message2 = mock(TextMessage.class);

        when(message1.getStringProperty("JMSXGroupID")).thenReturn(partitionKey1);
        when(message1.getStringProperty("messageKey")).thenReturn(partitionKey1);
        when(message1.getJMSTimestamp()).thenReturn(timestamp);
        when(message1.getText()).thenReturn(messageBody);

        when(message2.getStringProperty("JMSXGroupID")).thenReturn(partitionKey2);
        when(message2.getStringProperty("messageKey")).thenReturn(partitionKey2);
        when(message2.getJMSTimestamp()).thenReturn(timestamp);
        when(message2.getText()).thenReturn(messageBody);

        List<PartitionAwareMessage> messages = new ArrayList<>();
        Consumer<PartitionAwareMessage> handler = messages::add;

        consumer.subscribePartitionAware(topic, handler);

        // When
        consumer.processMessageWithPartition(topic, message1);
        consumer.processMessageWithPartition(topic, message2);

        // Then
        assertEquals(2, messages.size());
        assertNotEquals(messages.get(0).getPartitionKey(), messages.get(1).getPartitionKey());
    }

    @Test
    void testExtractPartitionInfo_TracksPartitionKeys() throws JMSException {
        // Given
        String topic = "test-topic";
        String[] partitionKeys = {"key-1", "key-2", "key-3"};
        String messageBody = "{\"tradeId\":\"T001\"}";
        long timestamp = System.currentTimeMillis();

        List<TextMessage> messages = new ArrayList<>();
        for (String key : partitionKeys) {
            TextMessage msg = mock(TextMessage.class);
            when(msg.getStringProperty("JMSXGroupID")).thenReturn(key);
            when(msg.getStringProperty("messageKey")).thenReturn(key);
            when(msg.getJMSTimestamp()).thenReturn(timestamp);
            when(msg.getText()).thenReturn(messageBody);
            messages.add(msg);
        }

        Consumer<PartitionAwareMessage> handler = msg -> {};

        consumer.subscribePartitionAware(topic, handler);

        // When
        for (TextMessage msg : messages) {
            consumer.processMessageWithPartition(topic, msg);
        }

        // Then
        Set<String> trackedKeys = consumer.getPartitionKeys(topic);
        assertNotNull(trackedKeys);
        assertEquals(3, trackedKeys.size());
        assertTrue(trackedKeys.contains("key-1"));
        assertTrue(trackedKeys.contains("key-2"));
        assertTrue(trackedKeys.contains("key-3"));
    }

    @Test
    void testExtractPartitionInfo_NoPartitionKey() throws JMSException {
        // Given
        String topic = "test-topic";
        String messageBody = "{\"tradeId\":\"T001\"}";
        long timestamp = System.currentTimeMillis();

        when(textMessage.getStringProperty("JMSXGroupID")).thenReturn(null);
        when(textMessage.getStringProperty("Solace_Partition_Key")).thenReturn(null);
        when(textMessage.getStringProperty("messageKey")).thenReturn(null);
        when(textMessage.getJMSTimestamp()).thenReturn(timestamp);
        when(textMessage.getText()).thenReturn(messageBody);

        AtomicReference<PartitionAwareMessage> capturedMessage = new AtomicReference<>();
        Consumer<PartitionAwareMessage> handler = capturedMessage::set;

        consumer.subscribePartitionAware(topic, handler);

        // When
        consumer.processMessageWithPartition(topic, textMessage);

        // Then
        assertNotNull(capturedMessage.get());
        PartitionAwareMessage message = capturedMessage.get();
        assertNull(message.getPartitionKey());
        assertNull(message.getPartitionId());
    }
}
