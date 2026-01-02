package com.bank.esps.infrastructure.messaging.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test cases to verify KafkaMessageProducer correctly sets partition keys
 */
@ExtendWith(MockitoExtension.class)
class KafkaMessageProducerPartitionTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaMessageProducer producer;

    @BeforeEach
    void setUp() {
        producer = new KafkaMessageProducer(kafkaTemplate, Optional.empty());
    }

    @Test
    void testSendMessageWithKey_SetsPartitionKey() {
        // Given
        String topic = "test-topic";
        String key = "position-key-123";
        String message = "{\"tradeId\":\"T001\"}";

        // When
        producer.send(topic, key, message);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        
        ProducerRecord<String, Object> record = recordCaptor.getValue();
        assertNotNull(record);
        assertEquals(topic, record.topic());
        assertEquals(key, record.key());
        assertEquals(message, record.value());
    }

    @Test
    void testSendMessageWithoutKey_NoPartitionKey() {
        // Given
        String topic = "test-topic";
        String message = "{\"tradeId\":\"T001\"}";

        // When
        producer.send(topic, null, message);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        
        ProducerRecord<String, Object> record = recordCaptor.getValue();
        assertNotNull(record);
        assertEquals(topic, record.topic());
        assertNull(record.key());
        assertEquals(message, record.value());
    }

    @Test
    void testSendMessageWithSameKey_ConsistentPartitioning() {
        // Given
        String topic = "test-topic";
        String key = "position-key-456";
        String message1 = "{\"tradeId\":\"T001\"}";
        String message2 = "{\"tradeId\":\"T002\"}";

        // When
        producer.send(topic, key, message1);
        producer.send(topic, key, message2);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(recordCaptor.capture());
        
        var records = recordCaptor.getAllValues();
        assertEquals(2, records.size());
        
        // Both messages should have the same key
        assertEquals(key, records.get(0).key());
        assertEquals(key, records.get(1).key());
        
        // Kafka will route both to the same partition (hash-based)
        // Note: We can't verify the actual partition number without a real Kafka broker,
        // but we verify the key is set correctly
    }

    @Test
    void testSendMessageWithDifferentKeys_DifferentPartitionKeys() {
        // Given
        String topic = "test-topic";
        String key1 = "position-key-111";
        String key2 = "position-key-222";
        String message = "{\"tradeId\":\"T001\"}";

        // When
        producer.send(topic, key1, message);
        producer.send(topic, key2, message);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(recordCaptor.capture());
        
        var records = recordCaptor.getAllValues();
        assertEquals(2, records.size());
        
        // Messages should have different keys
        assertEquals(key1, records.get(0).key());
        assertEquals(key2, records.get(1).key());
        assertNotEquals(records.get(0).key(), records.get(1).key());
    }

    @Test
    void testSendMessageWithUserContext_IncludesHeaders() {
        // Given
        String topic = "test-topic";
        String key = "position-key-789";
        String message = "{\"tradeId\":\"T001\"}";
        com.bank.esps.domain.auth.UserContext userContext = 
            com.bank.esps.domain.auth.UserContext.builder()
                .userId("user123")
                .build();

        // When
        producer.send(topic, key, message, userContext);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        
        ProducerRecord<String, Object> record = recordCaptor.getValue();
        assertNotNull(record);
        assertEquals(key, record.key());
        
        // Verify user context is in headers
        var headers = record.headers();
        assertNotNull(headers);
        assertTrue(headers.headers("user-id").iterator().hasNext());
    }

    @Test
    void testSendMessage_EmptyKeyHandled() {
        // Given
        String topic = "test-topic";
        String key = "";
        String message = "{\"tradeId\":\"T001\"}";

        // When
        producer.send(topic, key, message);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        
        ProducerRecord<String, Object> record = recordCaptor.getValue();
        // Empty key should be treated as null
        assertTrue(record.key() == null || record.key().isEmpty());
    }
}
