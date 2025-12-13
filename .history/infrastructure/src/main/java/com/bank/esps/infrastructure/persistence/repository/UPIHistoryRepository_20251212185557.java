package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.UPIHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UPI history
 */
@Repository
public interface UPIHistoryRepository extends JpaRepository<UPIHistoryEntity, UUID> {
    
    /**
     * Find all UPI changes for a position
     */
    List<UPIHistoryEntity> findByPositionKeyOrderByOccurredAtDesc(String positionKey);
    
    /**
     * Find all UPI changes for a specific UPI
     */
    List<UPIHistoryEntity> findByUpiOrderByOccurredAtDesc(String upi);
    
    /**
     * Find the most recent UPI change for a position
     */
    @Query("SELECT u FROM UPIHistoryEntity u WHERE u.positionKey = :positionKey ORDER BY u.occurredAt DESC")
    Optional<UPIHistoryEntity> findMostRecentByPositionKey(@Param("positionKey") String positionKey);
    
    /**
     * Find UPI changes by change type
     */
    List<UPIHistoryEntity> findByChangeTypeOrderByOccurredAtDesc(String changeType);
    
    /**
     * Find merge events
     */
    @Query("SELECT u FROM UPIHistoryEntity u WHERE u.changeType = 'MERGED' AND u.mergedFromPositionKey IS NOT NULL")
    List<UPIHistoryEntity> findMergeEvents();
    
    /**
     * Find UPI changes after a given time
     */
    @Query("SELECT u FROM UPIHistoryEntity u WHERE u.occurredAt >= :after ORDER BY u.occurredAt DESC")
    List<UPIHistoryEntity> findChangesAfter(@Param("after") OffsetDateTime after);
    
    /**
     * Find UPI changes for a position within a date range
     */
    @Query("SELECT u FROM UPIHistoryEntity u WHERE u.positionKey = :positionKey " +
           "AND u.effectiveDate >= :fromDate AND u.effectiveDate <= :toDate " +
           "ORDER BY u.effectiveDate DESC, u.occurredAt DESC")
    List<UPIHistoryEntity> findChangesInDateRange(
            @Param("positionKey") String positionKey,
            @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate);
}
