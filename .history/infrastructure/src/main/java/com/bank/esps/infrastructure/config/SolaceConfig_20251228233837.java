package com.bank.esps.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
    
    @Value("${solace.java.host:tcp://localhost:55556}")
    private String solaceHost;
    
    @Value("${solace.java.msg-vpn:default}")
    private String solaceVpn;
    
    @Value("${solace.java.client-username:admin}")
    private String solaceUsername;
    
    @Value("${solace.java.client-password:admin}")
    private String solacePassword;
    
    /**
     * Create Solace ConnectionFactory bean
     * Solace JMS uses javax.jms.ConnectionFactory, but Spring Boot 3.x uses jakarta.jms.ConnectionFactory
     * We create an adapter to bridge the gap
     */
    @Bean
    @Primary
    public ConnectionFactory solaceConnectionFactory() {
        try {
            log.info("Creating Solace ConnectionFactory: host={}, vpn={}, username={}", 
                    solaceHost, solaceVpn, solaceUsername);
            
            // Try to use Solace JMS classes
            Class<?> solaceJmsUtilityClass = Class.forName("com.solacesystems.jms.SolJmsUtility");
            Class<?> solConnectionFactoryClass = Class.forName("com.solacesystems.jms.SolConnectionFactory");
            
            Object factory = solaceJmsUtilityClass.getMethod("createConnectionFactory").invoke(null);
            
            // Set connection properties using reflection
            solConnectionFactoryClass.getMethod("setHost", String.class).invoke(factory, solaceHost);
            solConnectionFactoryClass.getMethod("setVPN", String.class).invoke(factory, solaceVpn);
            solConnectionFactoryClass.getMethod("setUsername", String.class).invoke(factory, solaceUsername);
            solConnectionFactoryClass.getMethod("setPassword", String.class).invoke(factory, solacePassword);
            
            // Try to set directTransport if method exists (optional - not all versions have this)
            try {
                java.lang.reflect.Method setDirectTransportMethod = solConnectionFactoryClass.getMethod("setDirectTransport", boolean.class);
                setDirectTransportMethod.invoke(factory, false);
                log.debug("setDirectTransport set to false");
            } catch (NoSuchMethodException e) {
                log.debug("setDirectTransport method not available in this Solace JMS version, skipping");
            } catch (Exception e) {
                log.warn("Failed to set directTransport, continuing anyway: {}", e.getMessage());
            }
            
            // Solace JMS uses javax.jms.ConnectionFactory, but Spring Boot 3.x uses jakarta.jms.ConnectionFactory
            // Create an adapter that wraps the javax factory
            javax.jms.ConnectionFactory javaxFactory = (javax.jms.ConnectionFactory) factory;
            
            // Use SingleConnectionFactory which is more flexible with javax/jakarta compatibility
            // We'll wrap it in CachingConnectionFactory for better performance
            org.springframework.jms.connection.SingleConnectionFactory singleFactory = 
                new org.springframework.jms.connection.SingleConnectionFactory();
            
            // Use reflection to set the target connection factory
            try {
                java.lang.reflect.Method setTargetMethod = org.springframework.jms.connection.SingleConnectionFactory.class
                    .getMethod("setTargetConnectionFactory", Object.class);
                setTargetMethod.invoke(singleFactory, javaxFactory);
                log.debug("Set targetConnectionFactory on SingleConnectionFactory using reflection");
            } catch (Exception e) {
                // Try with jakarta.jms.ConnectionFactory parameter
                try {
                    java.lang.reflect.Method setTargetMethod = org.springframework.jms.connection.SingleConnectionFactory.class
                        .getMethod("setTargetConnectionFactory", jakarta.jms.ConnectionFactory.class);
                    // Use reflection to bypass type checking - this will fail at runtime if incompatible
                    java.lang.reflect.Method invokeMethod = java.lang.reflect.Method.class.getMethod("invoke", Object.class, Object[].class);
                    invokeMethod.invoke(setTargetMethod, singleFactory, new Object[]{javaxFactory});
                    log.debug("Set targetConnectionFactory using jakarta.jms.ConnectionFactory method");
                } catch (Exception e2) {
                    log.error("Failed to set target connection factory using reflection", e2);
                    // Last resort: try to set via field
                    try {
                        java.lang.reflect.Field targetField = org.springframework.jms.connection.SingleConnectionFactory.class
                            .getDeclaredField("targetConnectionFactory");
                        targetField.setAccessible(true);
                        targetField.set(singleFactory, javaxFactory);
                        targetField.setAccessible(false);
                        log.debug("Set targetConnectionFactory field directly");
                    } catch (Exception e3) {
                        throw new IllegalStateException("Failed to set target connection factory. " +
                                "Cannot bridge javax.jms.ConnectionFactory to jakarta.jms.ConnectionFactory.", e3);
                    }
                }
            }
            
            singleFactory.setReconnectOnException(true);
            
            // Wrap in CachingConnectionFactory for better performance
            org.springframework.jms.connection.CachingConnectionFactory cachingFactory = 
                new org.springframework.jms.connection.CachingConnectionFactory();
            cachingFactory.setTargetConnectionFactory(singleFactory);
            cachingFactory.setSessionCacheSize(10);
            cachingFactory.setCacheConsumers(true);
            cachingFactory.setCacheProducers(true);
            
            log.info("Successfully created Solace ConnectionFactory adapter: {}", cachingFactory.getClass().getName());
            return cachingFactory;
            
        } catch (Exception e) {
            log.error("Failed to create Solace ConnectionFactory manually", e);
            throw new IllegalStateException("Failed to create Solace ConnectionFactory. " +
                    "Ensure solace-jms-spring-boot-starter is in classpath and Solace JMS classes are available.", e);
        }
    }
    
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
     * Uses the ConnectionFactory bean we created
     */
    @Bean("solaceJmsTemplate")
    public JmsTemplate solaceJmsTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        
        log.info("Creating Solace JMS Template with ConnectionFactory: {}", 
                connectionFactory.getClass().getName());
        
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setDeliveryPersistent(true); // Guaranteed delivery
        template.setExplicitQosEnabled(true);
        template.setDeliveryMode(DeliveryMode.PERSISTENT);
        template.setTimeToLive(86400000); // 24 hours TTL
        log.info("Solace JMS Template configured successfully");
        return template;
    }
    
    /**
     * JMS Listener Container Factory for receiving messages
     * Uses the ConnectionFactory bean we created
     */
    @Bean("jmsListenerContainerFactory")
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        
        log.info("Creating Solace JMS Listener Container Factory with ConnectionFactory: {}", 
                connectionFactory.getClass().getName());
        
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrency("1-10"); // 1 to 10 concurrent consumers
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        factory.setErrorHandler(t -> {
            log.error("JMS Listener error", t);
        });
        log.info("Solace JMS Listener Container Factory configured successfully");
        return factory;
    }
}
