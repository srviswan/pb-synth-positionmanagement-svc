# Messaging Abstraction Layer

## Overview

The messaging abstraction layer allows the application to be independent of the underlying messaging system (Kafka, Solace, RabbitMQ, etc.). This enables switching messaging systems without changing application code.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Application Layer                       │
│  (TradeProcessingService, RecalculationService)         │
│                    ↓ uses                                │
│  ┌──────────────────────────────────────────────────┐ │
│  │     MessageProducer / MessageConsumer              │ │
│  │     (Interfaces in domain module)                  │ │
│  └──────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
                    ↓ implements
┌─────────────────────────────────────────────────────────┐
│              Infrastructure Layer                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  KafkaMessageProducer / KafkaMessageConsumer      │  │
│  │  (Kafka implementation)                           │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  SolaceMessageProducer / SolaceMessageConsumer    │  │
│  │  (Solace implementation - example)                 │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## Interfaces

### MessageProducer

Located in: `com.bank.esps.domain.messaging.MessageProducer`

**Methods:**
- `publishBackdatedTrade(TradeEvent)` - Publish to coldpath
- `publishToDLQ(TradeEvent, String)` - Publish to Dead Letter Queue
- `publishToErrorQueue(TradeEvent, String)` - Publish to error queue
- `publishCorrectionEvent(String, String)` - Publish correction events

### MessageConsumer

Located in: `com.bank.esps.domain.messaging.MessageConsumer`

**Methods:**
- `subscribeToBackdatedTrades(Consumer<TradeEvent>)` - Subscribe to backdated trades
- `start()` - Start consuming
- `stop()` - Stop consuming
- `isRunning()` - Check if running

## Current Implementation

**Default:** Kafka implementation
- `KafkaMessageProducer` - Located in `infrastructure` module
- `KafkaMessageConsumer` - Located in `infrastructure` module

## Switching to Solace (Example)

### Step 1: Add Solace Dependencies

Add to `infrastructure/pom.xml`:

```xml
<dependency>
    <groupId>com.solace.spring.boot</groupId>
    <artifactId>solace-jms-spring-boot-starter</artifactId>
    <version>4.0.0</version>
</dependency>
```

### Step 2: Create Solace Implementation

Create `SolaceMessageProducer.java`:

```java
@Component("solaceMessageProducer")
public class SolaceMessageProducer implements MessageProducer {
    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;
    
    // Implement all MessageProducer methods using JmsTemplate
}
```

Create `SolaceMessageConsumer.java`:

```java
@Component("solaceMessageConsumer")
public class SolaceMessageConsumer implements MessageConsumer {
    // Implement all MessageConsumer methods using JMS listeners
}
```

### Step 3: Configure Spring to Use Solace

**Option A: Use @Primary**

```java
@Configuration
public class SolaceMessagingConfig {
    @Bean
    @Primary
    public MessageProducer messageProducer(
            @Qualifier("solaceMessageProducer") MessageProducer producer) {
        return producer;
    }
    
    @Bean
    @Primary
    public MessageConsumer messageConsumer(
            @Qualifier("solaceMessageConsumer") MessageConsumer consumer) {
        return consumer;
    }
}
```

**Option B: Use Profile**

```java
@Configuration
@Profile("solace")
public class SolaceMessagingConfig {
    // Same as above
}
```

Then set: `spring.profiles.active=solace`

**Option C: Use @Qualifier in Services**

```java
@Service
public class TradeProcessingService {
    public TradeProcessingService(
            @Qualifier("solaceMessageProducer") MessageProducer producer) {
        // ...
    }
}
```

## Configuration

The default configuration in `MessagingConfig.java` automatically wires Kafka implementations. To override:

1. **Create your implementation** with `@Component("yourMessageProducer")`
2. **Override the bean** using one of the methods above
3. **No application code changes needed!**

## Benefits

✅ **Decoupling**: Application code doesn't depend on messaging implementation  
✅ **Testability**: Easy to mock for unit tests  
✅ **Flexibility**: Switch messaging systems without code changes  
✅ **Multiple Implementations**: Can support multiple messaging systems simultaneously  
✅ **Clean Architecture**: Follows dependency inversion principle  

## Example: Using Both Kafka and Solace

You can have both implementations and route different messages to different systems:

```java
@Service
public class TradeProcessingService {
    private final MessageProducer kafkaProducer;
    private final MessageProducer solaceProducer;
    
    public void processTrade(TradeEvent trade) {
        if (trade.isHighPriority()) {
            solaceProducer.publishBackdatedTrade(trade); // Use Solace
        } else {
            kafkaProducer.publishBackdatedTrade(trade); // Use Kafka
        }
    }
}
```

## Testing

For unit tests, you can easily mock the interfaces:

```java
@Mock
private MessageProducer messageProducer;

@Test
void testTradeProcessing() {
    when(messageProducer.publishBackdatedTrade(any())).thenReturn(CompletableFuture.completedFuture(null));
    // Test your service
}
```

## Migration Path

1. **Phase 1**: Keep both implementations, route by feature flag
2. **Phase 2**: Gradually migrate traffic
3. **Phase 3**: Remove old implementation

No application code changes required during migration!
