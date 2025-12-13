package com.bank.esps.application.config;

import com.bank.esps.domain.messaging.MessageConsumer;
import com.bank.esps.application.service.RecalculationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coldpath configuration
 * Wires up the message consumer with the recalculation service
 * This abstraction allows switching messaging systems (Kafka, Solace, etc.) without code changes
 */
@Configuration
public class ColdpathConfig {
    
    /**
     * Wire up the message consumer with recalculation service
     * Using CommandLineRunner to ensure consumer is initialized after all beans are created
     */
    @Bean
    public CommandLineRunner coldpathInitializer(
            MessageConsumer messageConsumer,
            RecalculationService recalculationService) {
        return args -> {
            // Subscribe to backdated trades
            messageConsumer.subscribeToBackdatedTrades(recalculationService::recalculatePosition);
            // Start consuming (if not auto-started)
            messageConsumer.start();
        };
    }
}
