package com.bank.esps.application.service;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.messaging.MessageProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for regulatory submission tracking
 * Tracks all regulatory submissions for compliance
 */
@Service
public class RegulatorySubmissionService {
    
    private static final Logger log = LoggerFactory.getLogger(RegulatorySubmissionService.class);
    
    private final MessageProducer messageProducer;
    private final ObjectMapper objectMapper;
    
    @Value("${app.kafka.topics.regulatory-submissions:regulatory-submissions}")
    private String regulatorySubmissionsTopic;
    
    public RegulatorySubmissionService(MessageProducer messageProducer, ObjectMapper objectMapper) {
        this.messageProducer = messageProducer;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Submit trade to regulatory authorities (hotpath)
     */
    public void submitTradeToRegulator(TradeEvent tradeEvent, PositionState positionState) {
        try {
            Map<String, Object> regulatoryEvent = new HashMap<>();
            regulatoryEvent.put("submissionType", "TRADE_REPORT");
            regulatoryEvent.put("tradeId", tradeEvent.getTradeId());
            regulatoryEvent.put("positionKey", tradeEvent.getPositionKey());
            regulatoryEvent.put("account", tradeEvent.getAccount());
            regulatoryEvent.put("instrument", tradeEvent.getInstrument());
            regulatoryEvent.put("quantity", tradeEvent.getQuantity());
            regulatoryEvent.put("price", tradeEvent.getPrice());
            regulatoryEvent.put("tradeDate", tradeEvent.getTradeDate());
            regulatoryEvent.put("effectiveDate", tradeEvent.getEffectiveDate());
            regulatoryEvent.put("submittedAt", java.time.OffsetDateTime.now());
            
            String eventJson = objectMapper.writeValueAsString(regulatoryEvent);
            messageProducer.send(regulatorySubmissionsTopic, tradeEvent.getTradeId(), eventJson);
            
            log.debug("Submitted trade {} to regulatory authorities", tradeEvent.getTradeId());
        } catch (Exception e) {
            log.error("Failed to submit trade {} to regulatory authorities", tradeEvent.getTradeId(), e);
            // Don't throw - regulatory submission failure shouldn't block trade processing
        }
    }
    
    /**
     * Submit UPI invalidation event (coldpath)
     */
    public void submitUPIInvalidation(String upi, java.util.List<TradeEvent> affectedTrades) {
        try {
            Map<String, Object> invalidationEvent = new HashMap<>();
            invalidationEvent.put("submissionType", "UPI_INVALIDATION");
            invalidationEvent.put("upi", upi);
            invalidationEvent.put("affectedTrades", affectedTrades);
            invalidationEvent.put("submittedAt", java.time.OffsetDateTime.now());
            
            String eventJson = objectMapper.writeValueAsString(invalidationEvent);
            messageProducer.send(regulatorySubmissionsTopic, upi, eventJson);
            
            log.info("Submitted UPI invalidation for UPI: {}, affected trades: {}", upi, affectedTrades.size());
        } catch (Exception e) {
            log.error("Failed to submit UPI invalidation for UPI: {}", upi, e);
        }
    }
}
