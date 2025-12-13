package com.bank.esps.application.service;

import com.bank.esps.application.validation.TradeValidationService;
import com.bank.esps.domain.enums.TradeSequenceStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.kafka.TradeEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Main trade processing service
 * Routes trades to hotpath or coldpath based on classification
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeProcessingService {
    
    private final TradeClassifier tradeClassifier;
    private final TradeValidationService validationService;
    private final IdempotencyService idempotencyService;
    private final HotpathPositionService hotpathService;
    private final TradeEventProducer eventProducer;
    
    /**
     * Process trade event
     * Main entry point for trade processing
     */
    public void processTrade(TradeEvent tradeEvent) {
        // 1. Validate trade
        TradeValidationService.ValidationResult validation = validationService.validate(tradeEvent);
        if (!validation.isValid()) {
            log.error("Trade validation failed for {}: {}", tradeEvent.getTradeId(), validation.getErrors());
            eventProducer.publishToDLQ(tradeEvent, String.join(", ", validation.getErrors()));
            return;
        }
        
        // 2. Check idempotency
        if (idempotencyService.isProcessed(tradeEvent.getTradeId())) {
            log.warn("Trade {} already processed, skipping (idempotency)", tradeEvent.getTradeId());
            return;
        }
        
        // 3. Classify trade
        TradeSequenceStatus status = tradeClassifier.classifyTrade(tradeEvent);
        
        // 4. Route based on classification
        switch (status) {
            case CURRENT_DATED, FORWARD_DATED -> {
                // Process in hotpath
                log.debug("Routing trade {} to hotpath", tradeEvent.getTradeId());
                hotpathService.processCurrentDatedTrade(tradeEvent);
            }
            case BACKDATED -> {
                // Route to coldpath
                log.debug("Routing trade {} to coldpath", tradeEvent.getTradeId());
                eventProducer.publishBackdatedTrade(tradeEvent);
                // Also create provisional position (will be implemented in hotpath service)
            }
        }
    }
}
