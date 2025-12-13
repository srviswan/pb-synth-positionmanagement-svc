package com.bank.esps.application.service;

import com.bank.esps.domain.cache.CacheService;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.persistence.entity.IdempotencyEntity;
import com.bank.esps.infrastructure.persistence.repository.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service for managing idempotency
 * Prevents duplicate processing of trades
 * Uses cache for fast lookups with database as source of truth
 */
@Service
public class IdempotencyService {
    
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    
    private static final String CACHE_KEY_PREFIX = "idempotency:trade:";
    private static final String CACHE_STATUS_PROCESSED = "PROCESSED";
    private static final String CACHE_STATUS_FAILED = "FAILED";
    
    private final IdempotencyRepository idempotencyRepository;
    private final CacheService cacheService;
    private final Duration cacheTtl;
    
    public IdempotencyService(
            IdempotencyRepository idempotencyRepository,
            CacheService cacheService,
            @Value("${app.cache.default-ttl:PT24H}") Duration defaultTtl) {
        this.idempotencyRepository = idempotencyRepository;
        this.cacheService = cacheService;
        this.cacheTtl = defaultTtl;
    }
    
    /**
     * Check if trade has already been processed
     * Uses cache for fast lookup, falls back to database if cache miss
     */
    public boolean isProcessed(String tradeId) {
        if (tradeId == null || tradeId.isEmpty()) {
            return false;
        }
        
        String cacheKey = CACHE_KEY_PREFIX + tradeId;
        
        // Check cache first
        String cachedStatus = cacheService.get(cacheKey, String.class).orElse(null);
        if (CACHE_STATUS_PROCESSED.equals(cachedStatus)) {
            log.debug("Trade {} found in cache as processed", tradeId);
            return true;
        }
        if (CACHE_STATUS_FAILED.equals(cachedStatus)) {
            log.debug("Trade {} found in cache as failed", tradeId);
            return false; // Failed trades can be retried
        }
        
        // Cache miss - check database
        boolean exists = idempotencyRepository.existsByTradeId(tradeId);
        
        // Update cache if found in database
        if (exists) {
            // Get the actual status from database and cache it
            idempotencyRepository.findByTradeId(tradeId).ifPresent(entity -> {
                String status = entity.getStatus();
                cacheService.put(cacheKey, status, cacheTtl);
                log.debug("Cached idempotency status for trade {}: {}", tradeId, status);
            });
        }
        
        return exists;
    }
    
    /**
     * Mark trade as processed
     * Updates both database and cache
     */
    @Transactional
    public void markAsProcessed(TradeEvent trade, Long eventVersion) {
        String idempotencyKey = generateIdempotencyKey(trade);
        
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setIdempotencyKey(idempotencyKey);
        entity.setTradeId(trade.getTradeId());
        entity.setPositionKey(trade.getPositionKey());
        entity.setEventVersion(eventVersion);
        entity.setStatus(CACHE_STATUS_PROCESSED);
        entity.setCorrelationId(trade.getCorrelationId());
        entity.setProcessedAt(OffsetDateTime.now());
        
        idempotencyRepository.save(entity);
        
        // Update cache
        String cacheKey = CACHE_KEY_PREFIX + trade.getTradeId();
        cacheService.put(cacheKey, CACHE_STATUS_PROCESSED, cacheTtl);
        
        log.debug("Marked trade {} as processed with idempotency key {}", trade.getTradeId(), idempotencyKey);
    }
    
    /**
     * Mark trade as failed
     * Updates both database and cache
     */
    @Transactional
    public void markAsFailed(TradeEvent trade, String error) {
        String idempotencyKey = generateIdempotencyKey(trade);
        
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setIdempotencyKey(idempotencyKey);
        entity.setTradeId(trade.getTradeId());
        entity.setPositionKey(trade.getPositionKey());
        entity.setStatus(CACHE_STATUS_FAILED);
        entity.setCorrelationId(trade.getCorrelationId());
        entity.setProcessedAt(OffsetDateTime.now());
        
        idempotencyRepository.save(entity);
        
        // Update cache
        String cacheKey = CACHE_KEY_PREFIX + trade.getTradeId();
        cacheService.put(cacheKey, CACHE_STATUS_FAILED, cacheTtl);
        
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
