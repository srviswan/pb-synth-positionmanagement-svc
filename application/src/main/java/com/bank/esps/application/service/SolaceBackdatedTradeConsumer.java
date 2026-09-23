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
     * JMS Listener for backdated trades from Solace with partition awareness
     * This replaces the @KafkaListener when using Solace
     */
    @JmsListener(destination = "${app.solace.topics.backdated-trades:backdated-trades}", 
                 containerFactory = "jmsListenerContainerFactory",
                 subscription = "${app.solace.topics.backdated-trades:backdated-trades}")
    @Transactional
    public void processBackdatedTrade(jakarta.jms.Message message) {
        try {
            if (!(message instanceof jakarta.jms.TextMessage)) {
                log.warn("Received non-text message from Solace");
                return;
            }
            
            jakarta.jms.TextMessage textMessage = (jakarta.jms.TextMessage) message;
            String tradeJson = textMessage.getText();
            TradeEvent backdatedTrade = objectMapper.readValue(tradeJson, TradeEvent.class);
            
            // Extract partition information from JMS properties
            String partitionKey = textMessage.getStringProperty("JMSXGroupID");
            if (partitionKey == null) {
                partitionKey = textMessage.getStringProperty("Solace_Partition_Key");
            }
            if (partitionKey == null) {
                partitionKey = textMessage.getStringProperty("messageKey");
            }
            
            Integer partitionId = null;
            try {
                String partitionIdStr = textMessage.getStringProperty("Solace_Partition_ID");
                if (partitionIdStr != null) {
                    partitionId = Integer.parseInt(partitionIdStr);
                }
            } catch (Exception e) {
                // Partition ID not available
            }
            
            // Extract user context
            String userId = null;
            try {
                userId = textMessage.getStringProperty("user-id");
            } catch (jakarta.jms.JMSException e) {
                log.debug("No user-id property in JMS message");
            }
            
            if (userId != null) {
                log.info("Processing backdated trade from Solace in coldpath: tradeId={}, positionKey={}, effectiveDate={}, userId={}, partitionKey={}, partitionId={}", 
                        backdatedTrade.getTradeId(), backdatedTrade.getPositionKey(), 
                        backdatedTrade.getEffectiveDate(), userId, partitionKey, partitionId);
            } else {
                log.info("Processing backdated trade from Solace in coldpath: tradeId={}, positionKey={}, effectiveDate={}, partitionKey={}, partitionId={} (no user context)", 
                        backdatedTrade.getTradeId(), backdatedTrade.getPositionKey(), 
                        backdatedTrade.getEffectiveDate(), partitionKey, partitionId);
            }
            
            // Track partition metrics if needed
            // metricsService.recordPartitionProcessing(partitionKey);
            
            coldpathRecalculationService.recalculatePosition(backdatedTrade);
            
        } catch (Exception e) {
            log.error("Failed to process backdated trade from Solace", e);
            throw new RuntimeException("Failed to process backdated trade from Solace", e);
        }
    }
}
