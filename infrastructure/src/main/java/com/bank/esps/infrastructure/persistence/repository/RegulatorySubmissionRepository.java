package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.RegulatorySubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for regulatory submissions
 */
@Repository
public interface RegulatorySubmissionRepository extends JpaRepository<RegulatorySubmissionEntity, UUID> {
    
    /**
     * Find submissions by trade ID
     */
    List<RegulatorySubmissionEntity> findByTradeId(String tradeId);
    
    /**
     * Find submissions by status
     */
    List<RegulatorySubmissionEntity> findByStatus(String status);
    
    /**
     * Find submissions by position key
     */
    List<RegulatorySubmissionEntity> findByPositionKey(String positionKey);
    
    /**
     * Find pending submissions
     */
    @Query("SELECT r FROM RegulatorySubmissionEntity r WHERE r.status = 'PENDING'")
    List<RegulatorySubmissionEntity> findPendingSubmissions();
    
    /**
     * Find submissions after a given time
     */
    @Query("SELECT r FROM RegulatorySubmissionEntity r WHERE r.submittedAt >= :after")
    List<RegulatorySubmissionEntity> findSubmissionsAfter(@Param("after") OffsetDateTime after);
}
