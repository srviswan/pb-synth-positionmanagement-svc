# Messaging Configuration Guide

## Overview

You can now enable/disable messaging implementations directly from `application.yml` without code changes!

## Configuration in application.yml

```yaml
app:
  messaging:
    # Select messaging type: kafka, solace, rabbitmq, etc.
    # Default: kafka
    type: ${MESSAGING_TYPE:kafka}
    
    # Enable/disable specific implementations
    kafka:
      enabled: ${MESSAGING_KAFKA_ENABLED:true}
    solace:
      enabled: ${MESSAGING_SOLACE_ENABLED:false}
```

## Usage Examples

### Use Kafka (Default)

```yaml
app:
  messaging:
    type: kafka
    kafka:
      enabled: true
    solace:
      enabled: false
```

Or simply omit the configuration (Kafka is default).

### Switch to Solace

```yaml
app:
  messaging:
    type: solace
    kafka:
      enabled: false
    solace:
      enabled: true
```

### Use Environment Variables

```bash
# Set messaging type
export MESSAGING_TYPE=solace
export MESSAGING_KAFKA_ENABLED=false
export MESSAGING_SOLACE_ENABLED=true

# Run application
mvn spring-boot:run
```

### Kubernetes ConfigMap Example

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: position-service-config
data:
  application.yml: |
    app:
      messaging:
        type: solace
        kafka:
          enabled: false
        solace:
          enabled: true
```

## How It Works

1. **Conditional Bean Creation**: Each messaging implementation uses `@ConditionalOnProperty` to only be created when enabled
2. **Automatic Selection**: `MessagingConfig` automatically selects the implementation based on `app.messaging.type`
3. **No Code Changes**: Application services use interfaces, so switching is transparent

## Implementation Details

### Kafka Implementation
- **Bean Name**: `kafkaMessageProducer`, `kafkaMessageConsumer`
- **Enabled When**: `app.messaging.kafka.enabled=true` (default)
- **Condition**: `@ConditionalOnProperty(name = "app.messaging.kafka.enabled", havingValue = "true", matchIfMissing = true)`

### Solace Implementation
- **Bean Name**: `solaceMessageProducer`, `solaceMessageConsumer`
- **Enabled When**: `app.messaging.solace.enabled=true`
- **Condition**: `@ConditionalOnProperty(name = "app.messaging.solace.enabled", havingValue = "true", matchIfMissing = false)`

## Adding New Messaging Systems

1. **Create Implementation**:
   ```java
   @Component("rabbitmqMessageProducer")
   @ConditionalOnProperty(
       name = "app.messaging.rabbitmq.enabled",
       havingValue = "true",
       matchIfMissing = false
   )
   public class RabbitMQMessageProducer implements MessageProducer {
       // Implementation
   }
   ```

2. **Add Configuration**:
   ```yaml
   app:
     messaging:
       type: rabbitmq
       rabbitmq:
         enabled: true
   ```

3. **Update MessagingConfig** (add case for new type):
   ```java
   case "rabbitmq" -> rabbitmqProducer;
   ```

## Validation

The system will validate that:
- The selected messaging type has an enabled implementation
- If `app.messaging.type=solace` but `app.messaging.solace.enabled=false`, it will fail with a clear error message

## Benefits

✅ **Configuration-Driven**: Switch messaging systems via YAML  
✅ **Environment-Specific**: Different configs for dev/staging/prod  
✅ **No Code Changes**: Application code remains unchanged  
✅ **Type-Safe**: Compile-time checking of implementations  
✅ **Flexible**: Can have multiple implementations available, select at runtime  

## Troubleshooting

### Error: "Solace messaging is not available"

**Cause**: `app.messaging.type=solace` but Solace is not enabled or dependencies missing

**Solution**:
1. Set `app.messaging.solace.enabled=true`
2. Ensure Solace dependencies are in `pom.xml`
3. Or switch back to Kafka: `app.messaging.type=kafka`

### Error: "No bean named 'solaceMessageProducer'"

**Cause**: Solace implementation not loaded (disabled or missing dependencies)

**Solution**: Check that:
- `app.messaging.solace.enabled=true`
- Solace dependencies are present
- Solace configuration is correct

## Example: Multi-Environment Setup

### Development (application-dev.yml)
```yaml
app:
  messaging:
    type: kafka
    kafka:
      enabled: true
```

### Production (application-prod.yml)
```yaml
app:
  messaging:
    type: solace
    solace:
      enabled: true
    kafka:
      enabled: false
```

Run with: `spring.profiles.active=prod`
