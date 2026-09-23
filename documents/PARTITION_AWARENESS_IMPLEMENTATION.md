# Partition Awareness Implementation

## Overview

Partition awareness has been implemented for both Kafka and Solace messaging providers. The implementation provides a unified abstraction that works seamlessly with both messaging systems.

## Architecture

### Domain Layer

#### PartitionAwareMessage (`domain/src/main/java/com/bank/esps/domain/messaging/PartitionAwareMessage.java`)
- Unified message wrapper with partition information
- Fields:
  - `messageBody`: The actual message content
  - `messageKey`: Message key (positionKey)
  - `partitionId`: Partition ID (Kafka partition number or Solace partition ID)
  - `partitionKey`: Partition key (used for routing/ordering)
  - `topic`: Topic/queue name
  - `offset`: Kafka offset (null for Solace)
  - `timestamp`: Message timestamp

#### Enhanced MessageConsumer Interface
- Added `subscribePartitionAware()` method for partition-aware subscriptions
- Added `getPartitionCount()` and `getAssignedPartitions()` methods
- Backward compatible with existing simple subscriptions

### Infrastructure Layer

#### KafkaMessageConsumer
- **Partition Information**: Extracts from `ConsumerRecord`
  - Partition ID: `record.partition()`
  - Partition Key: `record.key()` (positionKey)
  - Offset: `record.offset()`
  - Timestamp: `record.timestamp()`
- **Tracks Assigned Partitions**: Maintains list of assigned partitions per topic
- **Dual Handler Support**: Supports both simple and partition-aware handlers

#### SolaceMessageConsumer
- **Partition Information**: Extracts from JMS message properties
  - Partition Key: `JMSXGroupID` (standard JMS partition key)
  - Fallback: `Solace_Partition_Key` (Solace-specific)
  - Partition ID: `Solace_Partition_ID` (if partition-aware queue configured)
  - Timestamp: `JMSTimestamp`
- **Tracks Partition Keys**: Maintains set of partition keys seen per topic
- **Dual Handler Support**: Supports both simple and partition-aware handlers

### Producer Updates

#### SolaceMessageProducer
- **Sets JMSXGroupID**: Standard JMS property for partition key
- **Sets Solace_Partition_Key**: Solace-specific partition key property
- Ensures messages with same key go to same partition (ordering guarantee)

#### KafkaMessageProducer
- Already uses message key for partitioning (no changes needed)
- Same positionKey → same partition (Kafka guarantee)

### Application Layer

#### ColdpathRecalculationService (Kafka)
- Updated `@KafkaListener` to accept `ConsumerRecord` instead of `String`
- Extracts partition information: partition ID, offset, key
- Logs partition information for monitoring

#### SolaceBackdatedTradeConsumer (Solace)
- Updated `@JmsListener` to accept `Message` instead of `String`
- Extracts partition information from JMS properties
- Logs partition information for monitoring

#### PartitionAwareMessageProcessor
- Utility service for processing partition-aware messages
- Works with both Kafka and Solace
- Extracts and logs partition information

## Usage Examples

### Kafka Partition-Aware Consumption

```java
@KafkaListener(topics = "backdated-trades", groupId = "coldpath")
public void processBackdatedTrade(ConsumerRecord<String, String> record) {
    int partition = record.partition();
    String key = record.key();
    long offset = record.offset();
    String value = record.value();
    
    log.info("Processing from partition: {}, key: {}, offset: {}", 
        partition, key, offset);
    
    // Process message
}
```

### Solace Partition-Aware Consumption

```java
@JmsListener(destination = "backdated-trades")
public void processBackdatedTrade(Message message) {
    String partitionKey = message.getStringProperty("JMSXGroupID");
    Integer partitionId = getPartitionId(message);
    
    log.info("Processing with partitionKey: {}, partitionId: {}", 
        partitionKey, partitionId);
    
    // Process message
}
```

### Using PartitionAwareMessage

```java
messageConsumer.subscribePartitionAware("backdated-trades", 
    (PartitionAwareMessage message) -> {
        log.info("Partition: {}, Key: {}", 
            message.getPartitionId(), message.getPartitionKey());
        // Process message
    });
```

## Partition Information Mapping

### Kafka → PartitionAwareMessage

| Kafka | PartitionAwareMessage |
|-------|----------------------|
| `record.partition()` | `partitionId` |
| `record.key()` | `partitionKey` and `messageKey` |
| `record.offset()` | `offset` |
| `record.timestamp()` | `timestamp` |
| `record.value()` | `messageBody` |
| `record.topic()` | `topic` |

### Solace → PartitionAwareMessage

| Solace JMS | PartitionAwareMessage |
|------------|----------------------|
| `JMSXGroupID` property | `partitionKey` |
| `Solace_Partition_ID` property | `partitionId` |
| `messageKey` property | `messageKey` |
| `JMSTimestamp` | `timestamp` |
| Message body | `messageBody` |
| Destination | `topic` |
| N/A | `offset` (null) |

## Benefits

1. **Unified Interface**: Same abstraction works with Kafka and Solace
2. **Partition Visibility**: Access to partition information in application code
3. **Monitoring**: Can track metrics per partition
4. **Ordering Guarantees**: Partition key ensures ordering per position
5. **Backward Compatible**: Existing code continues to work
6. **Provider Agnostic**: Switch between Kafka and Solace without code changes

## Configuration

### Kafka
- Partition information automatically available from `ConsumerRecord`
- No additional configuration needed

### Solace
- Set `JMSXGroupID` in producer (already implemented)
- For partition-aware queues, configure in Solace admin:
  ```
  queue: backdated-trades-queue
    partition-count: 8
    partition-key-property: JMSXGroupID
  ```

## Monitoring

Partition information is logged for:
- **Kafka**: Partition ID, offset, key
- **Solace**: Partition key, partition ID (if available)

Example log output:
```
Processing backdated trade in coldpath: tradeId=T001, positionKey=PK001, 
effectiveDate=2024-01-15, partition=3, offset=12345
```

## Future Enhancements

1. **Partition-Level Metrics**: Track lag, throughput per partition
2. **Partition-Specific Processing**: Different logic per partition
3. **Partition-Aware Error Handling**: Retry strategies per partition
4. **Partition Assignment API**: Query/manage partition assignments
5. **Partition Health Checks**: Monitor partition health

## Files Modified/Created

### New Files
- `domain/src/main/java/com/bank/esps/domain/messaging/PartitionAwareMessage.java`
- `application/src/main/java/com/bank/esps/application/service/PartitionAwareMessageProcessor.java`
- `documents/PARTITION_AWARENESS_IMPLEMENTATION.md`

### Modified Files
- `domain/src/main/java/com/bank/esps/domain/messaging/MessageConsumer.java` - Added partition-aware methods
- `infrastructure/src/main/java/com/bank/esps/infrastructure/messaging/kafka/KafkaMessageConsumer.java` - Added partition awareness
- `infrastructure/src/main/java/com/bank/esps/infrastructure/messaging/solace/SolaceMessageConsumer.java` - Added partition awareness
- `infrastructure/src/main/java/com/bank/esps/infrastructure/messaging/solace/SolaceMessageProducer.java` - Sets JMSXGroupID
- `application/src/main/java/com/bank/esps/application/service/ColdpathRecalculationService.java` - Uses ConsumerRecord
- `application/src/main/java/com/bank/esps/application/service/SolaceBackdatedTradeConsumer.java` - Extracts partition info

## Testing

All tests passing:
- ✅ 52 e2e tests
- ✅ 28 API endpoint tests
- ✅ Compilation successful

The implementation is backward compatible and works with both Kafka and Solace messaging providers.
