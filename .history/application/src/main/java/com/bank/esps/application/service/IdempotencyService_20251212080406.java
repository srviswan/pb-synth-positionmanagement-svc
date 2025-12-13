package com.bank.esps.application.service;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.persistence.entity.IdempotencyEntity;
import com.bank.esps.infrastructure.persistence.repository.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service for managing idempotency
 * Prevents duplicate processing of trades
 */
@Service
public class IdempotencyService {
    
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    
    private final IdempotencyRepository idempotencyRepository;
    
    public IdempotencyService(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }
    
    /**
     * Check if trade has already been processed
     */
    public boolean isProcessed(String tradeId) {
        return idempotencyRepository.existsByTradeId(tradeId);
    }
    
    /**
     * Mark trade as processed
     */
    @Transactional
    public void markAsProcessed(TradeEvent trade, Long eventVersion) {
        String idempotencyKey = generateIdempotencyKey(trade);
        
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setIdempotencyKey(idempotencyKey);
        entity.setTradeId(trade.getTradeId());
        entity.setPositionKey(trade.getPositionKey());
        entity.setEventVersion(eventVersion);
        entity.setStatus("PROCESSED");
        entity.setCorrelationId(trade.getCorrelationId());
        entity.setProcessedAt(OffsetDateTime.now());
        
        idempotencyRepository.save(entity);
        log.debug("Marked trade {} as processed with idempotency key {}", trade.getTradeId(), idempotencyKey);
    }
    
    /**
     * Mark trade as failed
     */
    @Transactional
    public void markAsFailed(TradeEvent trade, String error) {
        String idempotencyKey = generateIdempotencyKey(trade);
        
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setIdempotencyKey(idempotencyKey);
        entity.setTradeId(trade.getTradeId());
        entity.setPositionKey(trade.getPositionKey());
        entity.setStatus("FAILED");
        entity.setCorrelationId(trade.getCorrelationId());
        entity.setProcessedAt(OffsetDateTime.now());
        
        idempotencyRepository.save(entity);
        log.warn("Marked trade {} as failed: {}", trade.getTradeId(), error);
    }
    
    /**
     * Generate idempotency key from trade
     */
    private String generateIdempotencyKey(TradeEvent trade) {
        // Use trade ID as primary key, with position key as fallback
        return trade.getTradeId() != null ? trade.getTradeId() : 
               UUID.nameUUIDFromBytes((trade.getPositionKey() + trade.getEffectiveDate()).getBytes()).toString();
    }
}
