package com.bank.esps.application.config;

import com.bank.esps.domain.messaging.MessageConsumer;
import com.bank.esps.domain.messaging.MessageProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Messaging configuration
 * Wires up the messaging abstraction with implementations based on application.yml configuration
 * 
 * Configuration in application.yml:
 *   app:
 *     messaging:
 *       type: kafka  # or solace, rabbitmq, etc.
 *       kafka:
 *         enabled: true
 *       solace:
 *         enabled: false
 * 
 * To switch messaging systems, simply change the type in application.yml:
 *   app.messaging.type: solace
 * 
 * Or use environment variables:
 *   MESSAGING_TYPE=solace
 */
@Configuration
public class MessagingConfig {
    
    @Value("${app.messaging.type:kafka}")
    private String messagingType;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * Primary MessageProducer bean
     * Selected based on app.messaging.type property
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "messageProducer")
    public MessageProducer messageProducer(
            @Qualifier("kafkaMessageProducer") MessageProducer kafkaProducer) {
        
        String type = messagingType.toLowerCase();
        if ("solace".equals(type)) {
            // Try to get Solace producer if available
            try {
                MessageProducer solaceProducer = applicationContext.getBean("solaceMessageProducer", MessageProducer.class);
                return solaceProducer;
            } catch (Exception e) {
                throw new IllegalStateException("Solace messaging is not available. " +
                        "Ensure app.messaging.solace.enabled=true and Solace dependencies are present.", e);
            }
        } else {
            // Default to Kafka
            return kafkaProducer;
        }
    }
    
    /**
     * Primary MessageConsumer bean
     * Selected based on app.messaging.type property
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "messageConsumer")
    public MessageConsumer messageConsumer(
            @Qualifier("kafkaMessageConsumer") MessageConsumer kafkaConsumer) {
        
        String type = messagingType.toLowerCase();
        if ("solace".equals(type)) {
            // Try to get Solace consumer if available
            try {
                MessageConsumer solaceConsumer = applicationContext.getBean("solaceMessageConsumer", MessageConsumer.class);
                return solaceConsumer;
            } catch (Exception e) {
                throw new IllegalStateException("Solace messaging is not available. " +
                        "Ensure app.messaging.solace.enabled=true and Solace dependencies are present.", e);
            }
        } else {
            // Default to Kafka
            return kafkaConsumer;
        }
    }
}
