package com.bank.esps.infrastructure.messaging.solace;

import com.bank.esps.domain.auth.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test cases to verify SolaceMessageProducer correctly sets partition keys
 */
@ExtendWith(MockitoExtension.class)
class SolaceMessageProducerPartitionTest {

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private Session session;

    @Mock
    private TextMessage textMessage;

    private SolaceMessageProducer producer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws JMSException {
        objectMapper = new ObjectMapper();
        producer = new SolaceMessageProducer(jmsTemplate, objectMapper);
        
        when(session.createTextMessage(anyString())).thenReturn(textMessage);
    }

    @Test
    void testSendMessageWithKey_SetsJMSXGroupID() throws JMSException {
        // Given
        String topic = "test-topic";
        String key = "position-key-123";
        String message = "{\"tradeId\":\"T001\"}";

        // When
        producer.send(topic, key, message);

        // Then
        ArgumentCaptor<org.springframework.jms.core.MessageCreator> creatorCaptor = 
            ArgumentCaptor.forClass(org.springframework.jms.core.MessageCreator.class);
        verify(jmsTemplate).send(eq(topic), creatorCaptor.capture());
        
        // Execute the message creator to verify properties
        creatorCaptor.getValue().createMessage(session);
        
        verify(textMessage).setStringProperty("messageKey", key);
        verify(textMessage).setStringProperty("JMSCorrelationID", key);
        verify(textMessage).setStringProperty("JMSXGroupID", key);
        verify(textMessage).setStringProperty("Solace_Partition_Key", key);
    }

    @Test
    void testSendMessageWithKey_SetsSolacePartitionKey() throws JMSException {
        // Given
        String topic = "test-topic";
        String key = "position-key-456";
        String message = "{\"tradeId\":\"T002\"}";

        // When
        producer.send(topic, key, message);

        // Then
        ArgumentCaptor<org.springframework.jms.core.MessageCreator> creatorCaptor = 
            ArgumentCaptor.forClass(org.springframework.jms.core.MessageCreator.class);
        verify(jmsTemplate).send(eq(topic), creatorCaptor.capture());
        
        creatorCaptor.getValue().createMessage(session);
        
        // Verify Solace-specific partition key is set
        verify(textMessage).setStringProperty("Solace_Partition_Key", key);
    }

    @Test
    void testSendMessageWithSameKey_ConsistentPartitionKeys() throws JMSException {
        // Given
        String topic = "test-topic";
        String key = "position-key-789";
        String message1 = "{\"tradeId\":\"T001\"}";
        String message2 = "{\"tradeId\":\"T002\"}";

        // When
        producer.send(topic, key, message1);
        producer.send(topic, key, message2);

        // Then
        ArgumentCaptor<org.springframework.jms.core.MessageCreator> creatorCaptor = 
            ArgumentCaptor.forClass(org.springframework.jms.core.MessageCreator.class);
        verify(jmsTemplate, times(2)).send(eq(topic), creatorCaptor.capture());
        
        var creators = creatorCaptor.getAllValues();
        assertEquals(2, creators.size());
        
        // Both messages should set the same partition key
        creators.get(0).createMessage(session);
        verify(textMessage, atLeastOnce()).setStringProperty("JMSXGroupID", key);
        
        reset(textMessage);
        when(session.createTextMessage(anyString())).thenReturn(textMessage);
        
        creators.get(1).createMessage(session);
        verify(textMessage, atLeastOnce()).setStringProperty("JMSXGroupID", key);
    }

    @Test
    void testSendMessageWithoutKey_NoPartitionKey() throws JMSException {
        // Given
        String topic = "test-topic";
        String message = "{\"tradeId\":\"T001\"}";

        // When
        producer.send(topic, null, message);

        // Then
        ArgumentCaptor<org.springframework.jms.core.MessageCreator> creatorCaptor = 
            ArgumentCaptor.forClass(org.springframework.jms.core.MessageCreator.class);
        verify(jmsTemplate).send(eq(topic), creatorCaptor.capture());
        
        creatorCaptor.getValue().createMessage(session);
        
        // Should not set partition key properties when key is null
        verify(textMessage, never()).setStringProperty(eq("JMSXGroupID"), anyString());
        verify(textMessage, never()).setStringProperty(eq("Solace_Partition_Key"), anyString());
    }

    @Test
    void testSendMessageWithEmptyKey_NoPartitionKey() throws JMSException {
        // Given
        String topic = "test-topic";
        String key = "";
        String message = "{\"tradeId\":\"T001\"}";

        // When
        producer.send(topic, key, message);

        // Then
        ArgumentCaptor<org.springframework.jms.core.MessageCreator> creatorCaptor = 
            ArgumentCaptor.forClass(org.springframework.jms.core.MessageCreator.class);
        verify(jmsTemplate).send(eq(topic), creatorCaptor.capture());
        
        creatorCaptor.getValue().createMessage(session);
        
        // Empty key should not set partition key properties
        verify(textMessage, never()).setStringProperty(eq("JMSXGroupID"), anyString());
        verify(textMessage, never()).setStringProperty(eq("Solace_Partition_Key"), anyString());
    }

    @Test
    void testSendMessageWithUserContext_IncludesUserHeaders() throws JMSException {
        // Given
        String topic = "test-topic";
        String key = "position-key-999";
        String message = "{\"tradeId\":\"T001\"}";
        UserContext userContext = UserContext.builder()
            .userId("user123")
            .build();

        // When
        producer.send(topic, key, message, userContext);

        // Then
        ArgumentCaptor<org.springframework.jms.core.MessageCreator> creatorCaptor = 
            ArgumentCaptor.forClass(org.springframework.jms.core.MessageCreator.class);
        verify(jmsTemplate).send(eq(topic), creatorCaptor.capture());
        
        creatorCaptor.getValue().createMessage(session);
        
        // Verify partition key is still set
        verify(textMessage).setStringProperty("JMSXGroupID", key);
        // Verify user context is set
        verify(textMessage).setStringProperty("user-id", "user123");
    }

    @Test
    void testSendToQueue_SetsPartitionKey() throws JMSException {
        // Given
        String queueName = "test-queue";
        String key = "position-key-111";
        String message = "{\"tradeId\":\"T001\"}";

        // When
        producer.sendToQueue(queueName, key, message);

        // Then
        ArgumentCaptor<org.springframework.jms.core.MessageCreator> creatorCaptor = 
            ArgumentCaptor.forClass(org.springframework.jms.core.MessageCreator.class);
        verify(jmsTemplate).send(anyString(), creatorCaptor.capture());
        
        creatorCaptor.getValue().createMessage(session);
        
        verify(textMessage).setStringProperty("JMSXGroupID", key);
        verify(textMessage).setStringProperty("Solace_Partition_Key", key);
    }
}
