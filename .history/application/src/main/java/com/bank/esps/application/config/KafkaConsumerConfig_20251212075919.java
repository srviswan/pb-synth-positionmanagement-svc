package com.bank.esps.application.config;

import com.bank.esps.application.service.TradeProcessingService;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.kafka.TradeEventConsumer;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration to wire up Kafka consumer with application services
 * Breaks circular dependency between infrastructure and application modules
 */
@Configuration
public class KafkaConsumerConfig {
    
    private final TradeEventConsumer tradeEventConsumer;
    private final TradeProcessingService tradeProcessingService;
    
    public KafkaConsumerConfig(TradeEventConsumer tradeEventConsumer, 
                              TradeProcessingService tradeProcessingService) {
        this.tradeEventConsumer = tradeEventConsumer;
        this.tradeProcessingService = tradeProcessingService;
    }
    
    @PostConstruct
    public void configureConsumers() {
        // Wire up the consumer with the processing service
        tradeEventConsumer.setTradeProcessor(tradeProcessingService::processTrade);
    }
}
