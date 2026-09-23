package com.bank.esps.domain.messaging;

/**
 * Represents a message with partition awareness information
 * Works with both Kafka and Solace messaging providers
 */
public class PartitionAwareMessage {
    private final String messageBody;
    private final String messageKey;
    private final Integer partitionId;  // Kafka partition ID or Solace partition ID
    private final String partitionKey; // Partition key (positionKey, JMSXGroupID, etc.)
    private final String topic;
    private final Long offset;         // Kafka offset (null for Solace)
    private final Long timestamp;       // Message timestamp
    
    public PartitionAwareMessage(String messageBody, String messageKey, 
                                Integer partitionId, String partitionKey,
                                String topic, Long offset, Long timestamp) {
        this.messageBody = messageBody;
        this.messageKey = messageKey;
        this.partitionId = partitionId;
        this.partitionKey = partitionKey;
        this.topic = topic;
        this.offset = offset;
        this.timestamp = timestamp;
    }
    
    public String getMessageBody() {
        return messageBody;
    }
    
    public String getMessageKey() {
        return messageKey;
    }
    
    public Integer getPartitionId() {
        return partitionId;
    }
    
    public String getPartitionKey() {
        return partitionKey;
    }
    
    public String getTopic() {
        return topic;
    }
    
    public Long getOffset() {
        return offset;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public boolean hasPartitionInfo() {
        return partitionId != null || partitionKey != null;
    }
}
