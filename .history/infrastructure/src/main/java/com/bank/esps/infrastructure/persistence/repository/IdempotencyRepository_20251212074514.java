package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.IdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Repository for idempotency store operations
 * Prevents duplicate processing of trades
 */
@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyEntity, String> {
    
    /**
     * Find by idempotency key
     */
    Optional<IdempotencyEntity> findById(String idempotencyKey);
    
    /**
     * Find by trade ID
     */
    Optional<IdempotencyEntity> findByTradeId(String tradeId);
    
    /**
     * Check if trade has been processed
     */
    boolean existsByTradeId(String tradeId);
    
    /**
     * Delete old idempotency records (cleanup)
     */
    @Modifying
    @Query("DELETE FROM IdempotencyEntity i WHERE i.processedAt < :before")
    void deleteByProcessedAtBefore(@Param("before") OffsetDateTime before);
}
