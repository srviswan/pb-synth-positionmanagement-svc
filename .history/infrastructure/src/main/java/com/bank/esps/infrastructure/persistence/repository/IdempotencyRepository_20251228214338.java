package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.IdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for idempotency tracking
 */
@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyEntity, Long> {
    
    Optional<IdempotencyEntity> findByMessageId(String messageId);
}
