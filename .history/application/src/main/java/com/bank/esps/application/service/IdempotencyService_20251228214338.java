package com.bank.esps.application.service;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.persistence.entity.IdempotencyEntity;
import com.bank.esps.infrastructure.persistence.repository.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Service for idempotency checking
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
    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String tradeId) {
        Optional<IdempotencyEntity> existing = idempotencyRepository.findByMessageId(tradeId);
        if (existing.isPresent()) {
            log.debug("Trade {} already processed at {}", tradeId, existing.get().getProcessedAt());
            return true;
        }
        return false;
    }
    
    /**
     * Record that a trade has been processed
     */
    @Transactional
    public void recordProcessed(String tradeId, String positionKey, int eventVersion) {
        IdempotencyEntity entity = IdempotencyEntity.builder()
                .messageId(tradeId)
                .positionKey(positionKey)
                .status("PROCESSED")
                .eventVersion(eventVersion)
                .processedAt(OffsetDateTime.now())
                .build();
        
        idempotencyRepository.save(entity);
        log.debug("Recorded idempotency: tradeId={}, positionKey={}, version={}", 
                tradeId, positionKey, eventVersion);
    }
}
