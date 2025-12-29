package com.bank.esps.infrastructure.config;

import com.bank.esps.domain.messaging.MessageConsumer;
import com.bank.esps.domain.messaging.MessageProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration to select messaging implementation (Kafka or Solace)
 */
@Configuration
public class MessagingConfig {
    
    @Value("${app.messaging.provider:kafka}")
    private String messagingProvider;
    
    @Bean
    @Primary
    public MessageProducer messageProducer(
            @Qualifier("kafkaMessageProducer") MessageProducer kafkaProducer,
            @Qualifier("solaceMessageProducer") MessageProducer solaceProducer) {
        if ("solace".equalsIgnoreCase(messagingProvider)) {
            return solaceProducer;
        }
        return kafkaProducer; // Default to Kafka
    }
    
    @Bean
    @Primary
    public MessageConsumer messageConsumer(
            @Qualifier("kafkaMessageConsumer") MessageConsumer kafkaConsumer,
            @Qualifier("solaceMessageConsumer") MessageConsumer solaceConsumer) {
        if ("solace".equalsIgnoreCase(messagingProvider)) {
            return solaceConsumer;
        }
        return kafkaConsumer; // Default to Kafka
    }
}
