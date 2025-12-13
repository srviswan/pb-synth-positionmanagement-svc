package com.bank.esps.application.service;

import com.bank.esps.application.validation.TradeValidationService;
import com.bank.esps.domain.enums.TradeSequenceStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.kafka.TradeEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Main trade processing service
 * Routes trades to hotpath or coldpath based on classification
 */
@Service
public class TradeProcessingService {
    
    private static final Logger log = LoggerFactory.getLogger(TradeProcessingService.class);
    
    private final TradeClassifier tradeClassifier;
    private final TradeValidationService validationService;
    private final IdempotencyService idempotencyService;
    private final HotpathPositionService hotpathService;
    private final TradeEventProducer eventProducer;
    private final MetricsService metricsService;
    
    public TradeProcessingService(
            TradeClassifier tradeClassifier,
            TradeValidationService validationService,
            IdempotencyService idempotencyService,
            HotpathPositionService hotpathService,
            TradeEventProducer eventProducer,
            MetricsService metricsService) {
        this.tradeClassifier = tradeClassifier;
        this.validationService = validationService;
        this.idempotencyService = idempotencyService;
        this.hotpathService = hotpathService;
        this.eventProducer = eventProducer;
        this.metricsService = metricsService;
    }
    
    /**
     * Process trade event
     * Main entry point for trade processing
     */
    public void processTrade(TradeEvent tradeEvent) {
        metricsService.incrementTradesProcessed();
        
        // 1. Validate trade
        TradeValidationService.ValidationResult validation = validationService.validate(tradeEvent);
        if (!validation.isValid()) {
            log.error("Trade validation failed for {}: {}", tradeEvent.getTradeId(), validation.getErrors());
            metricsService.incrementValidationFailures();
            eventProducer.publishToDLQ(tradeEvent, String.join(", ", validation.getErrors()));
            return;
        }
        
        // 2. Check idempotency
        if (idempotencyService.isProcessed(tradeEvent.getTradeId())) {
            log.warn("Trade {} already processed, skipping (idempotency)", tradeEvent.getTradeId());
            metricsService.incrementIdempotencyHits();
            return;
        }
        
        // 3. Classify trade
        TradeSequenceStatus status = tradeClassifier.classifyTrade(tradeEvent);
        
        // 4. Route based on classification
        switch (status) {
            case CURRENT_DATED, FORWARD_DATED -> {
                // Process in hotpath
                log.debug("Routing trade {} to hotpath", tradeEvent.getTradeId());
                var sample = metricsService.startHotpathProcessing();
                try {
                    hotpathService.processCurrentDatedTrade(tradeEvent);
                    metricsService.incrementTradesProcessedHotpath();
                } catch (Exception e) {
                    metricsService.incrementErrors();
                    throw e;
                } finally {
                    metricsService.recordHotpathProcessing(sample);
                }
            }
            case BACKDATED -> {
                // Route to coldpath
                log.debug("Routing trade {} to coldpath", tradeEvent.getTradeId());
                metricsService.incrementBackdatedTrades();
                eventProducer.publishBackdatedTrade(tradeEvent);
                // Also create provisional position (will be implemented in hotpath service)
            }
        }
    }
}
