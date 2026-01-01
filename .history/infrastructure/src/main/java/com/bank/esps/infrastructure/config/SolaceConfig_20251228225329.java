package com.bank.esps.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Session;

/**
 * Solace JMS Configuration
 * Only active when app.messaging.provider=solace
 * 
 * Note: This configuration relies on Solace JMS Spring Boot Starter auto-configuration
 * for the ConnectionFactory. If using Solace, ensure solace-jms-spring-boot-starter
 * is in the classpath and Spring Boot will auto-configure the ConnectionFactory.
 */
@Configuration
@EnableJms
@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "solace", matchIfMissing = false)
public class SolaceConfig {
    
    private static final Logger log = LoggerFactory.getLogger(SolaceConfig.class);
    
    /**
     * JMS Message Converter for JSON
     */
    @Bean
    public MessageConverter jmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        log.info("Solace JMS Message Converter configured");
        return converter;
    }
    
    /**
     * JMS Template for sending messages
     * Uses auto-configured ConnectionFactory from Solace JMS Spring Boot Starter
     */
    @Bean("solaceJmsTemplate")
    public JmsTemplate solaceJmsTemplate(
            @Autowired(required = false) ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        
        if (connectionFactory == null) {
            log.error("ConnectionFactory not available. Solace JMS Spring Boot Starter may not be configured.");
            throw new IllegalStateException("Solace ConnectionFactory not available. " +
                    "Ensure solace-jms-spring-boot-starter is in classpath and Solace properties are configured.");
        }
        
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setDeliveryPersistent(true); // Guaranteed delivery
        template.setExplicitQosEnabled(true);
        template.setDeliveryMode(DeliveryMode.PERSISTENT);
        template.setTimeToLive(86400000); // 24 hours TTL
        log.info("Solace JMS Template configured with ConnectionFactory");
        return template;
    }
    
    /**
     * JMS Listener Container Factory for receiving messages
     * Uses auto-configured ConnectionFactory from Solace JMS Spring Boot Starter
     */
    @Bean("jmsListenerContainerFactory")
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            @Autowired(required = false) ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        
        if (connectionFactory == null) {
            log.error("ConnectionFactory not available. Solace JMS Spring Boot Starter may not be configured.");
            throw new IllegalStateException("Solace ConnectionFactory not available. " +
                    "Ensure solace-jms-spring-boot-starter is in classpath and Solace properties are configured.");
        }
        
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrency("1-10"); // 1 to 10 concurrent consumers
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        factory.setErrorHandler(t -> {
            log.error("JMS Listener error", t);
        });
        log.info("Solace JMS Listener Container Factory configured");
        return factory;
    }
}
