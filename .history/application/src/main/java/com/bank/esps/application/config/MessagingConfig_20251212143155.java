package com.bank.esps.application.config;

import com.bank.esps.domain.messaging.MessageConsumer;
import com.bank.esps.domain.messaging.MessageProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Messaging configuration
 * Wires up the messaging abstraction with implementations
 * 
 * By default, uses Kafka implementations. To switch to another messaging system:
 * 1. Create implementations of MessageProducer and MessageConsumer
 * 2. Annotate them with @Component and give them unique names
 * 3. Use @Primary or @Qualifier to select the implementation
 * 4. Or set spring.profiles.active to select a profile-specific configuration
 */
@Configuration
public class MessagingConfig {
    
    /**
     * Primary MessageProducer bean
     * Defaults to Kafka implementation, but can be overridden
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "messageProducer")
    public MessageProducer messageProducer(@Qualifier("kafkaMessageProducer") MessageProducer kafkaProducer) {
        return kafkaProducer;
    }
    
    /**
     * Primary MessageConsumer bean
     * Defaults to Kafka implementation, but can be overridden
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "messageConsumer")
    public MessageConsumer messageConsumer(@Qualifier("kafkaMessageConsumer") MessageConsumer kafkaConsumer) {
        return kafkaConsumer;
    }
}
