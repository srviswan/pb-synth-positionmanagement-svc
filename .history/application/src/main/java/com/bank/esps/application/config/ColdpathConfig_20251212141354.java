package com.bank.esps.application.config;

import com.bank.esps.application.service.RecalculationService;
import com.bank.esps.infrastructure.kafka.BackdatedTradeConsumer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coldpath configuration
 * Wires up the backdated trade consumer with the recalculation service
 */
@Configuration
public class ColdpathConfig {
    
    /**
     * Wire up the backdated trade consumer with recalculation service
     * Using CommandLineRunner to ensure consumer is initialized after all beans are created
     */
    @Bean
    public CommandLineRunner coldpathInitializer(
            BackdatedTradeConsumer consumer,
            RecalculationService recalculationService) {
        return args -> {
            consumer.setRecalculationProcessor(recalculationService::recalculatePosition);
        };
    }
}
