package com.bank.esps.application.service;

import com.bank.esps.domain.event.TradeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solace JMS consumer for backdated trades (coldpath)
 * Only active when app.messaging.provider=solace
 */
@Service
@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "solace", matchIfMissing = false)
public class SolaceBackdatedTradeConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(SolaceBackdatedTradeConsumer.class);
    
    private final ColdpathRecalculationService coldpathRecalculationService;
    private final ObjectMapper objectMapper;
    
    @Value("${app.solace.topics.backdated-trades:backdated-trades}")
    private String backdatedTradesTopic;
    
    @Autowired
    public SolaceBackdatedTradeConsumer(
            ColdpathRecalculationService coldpathRecalculationService,
            ObjectMapper objectMapper) {
        this.coldpathRecalculationService = coldpathRecalculationService;
        this.objectMapper = objectMapper;
        log.info("SolaceBackdatedTradeConsumer initialized for topic: {}", backdatedTradesTopic);
    }
    
    /**
     * JMS Listener for backdated trades from Solace
     * This replaces the @KafkaListener when using Solace
     */
    @JmsListener(destination = "${app.solace.topics.backdated-trades:backdated-trades}", 
                 containerFactory = "jmsListenerContainerFactory",
                 subscription = "${app.solace.topics.backdated-trades:backdated-trades}")
    @Transactional
    public void processBackdatedTrade(String tradeJson) {
        try {
            TradeEvent backdatedTrade = objectMapper.readValue(tradeJson, TradeEvent.class);
            log.info("Processing backdated trade from Solace in coldpath: tradeId={}, positionKey={}, effectiveDate={}", 
                    backdatedTrade.getTradeId(), backdatedTrade.getPositionKey(), backdatedTrade.getEffectiveDate());
            
            coldpathRecalculationService.recalculatePosition(backdatedTrade);
            
        } catch (Exception e) {
            log.error("Failed to process backdated trade from Solace: {}", tradeJson, e);
            throw new RuntimeException("Failed to process backdated trade from Solace", e);
        }
    }
}
