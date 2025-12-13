package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.ReconciliationBreakEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for reconciliation breaks
 */
@Repository
public interface ReconciliationBreakRepository extends JpaRepository<ReconciliationBreakEntity, UUID> {
    
    /**
     * Find breaks by position key
     */
    List<ReconciliationBreakEntity> findByPositionKey(String positionKey);
    
    /**
     * Find open breaks
     */
    @Query("SELECT r FROM ReconciliationBreakEntity r WHERE r.status = 'OPEN'")
    List<ReconciliationBreakEntity> findOpenBreaks();
    
    /**
     * Find breaks by status
     */
    List<ReconciliationBreakEntity> findByStatus(String status);
    
    /**
     * Find breaks detected after a given time
     */
    @Query("SELECT r FROM ReconciliationBreakEntity r WHERE r.detectedAt >= :after")
    List<ReconciliationBreakEntity> findBreaksDetectedAfter(@Param("after") OffsetDateTime after);
}
