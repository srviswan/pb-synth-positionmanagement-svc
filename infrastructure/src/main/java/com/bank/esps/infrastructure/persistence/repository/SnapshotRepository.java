package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for snapshot store operations
 * Handles optimistic locking and reconciliation status
 */
@Repository
public interface SnapshotRepository extends JpaRepository<SnapshotEntity, String> {
    
    /**
     * Find by position key with optimistic locking
     */
    Optional<SnapshotEntity> findById(String positionKey);
    
    /**
     * Find all snapshots by reconciliation status
     */
    List<SnapshotEntity> findAllByReconciliationStatus(ReconciliationStatus status);
    
    /**
     * Find all snapshots updated before a given time (for cleanup)
     */
    @Query("SELECT s FROM SnapshotEntity s WHERE s.lastUpdatedAt < :before")
    List<SnapshotEntity> findAllByLastUpdatedBefore(@Param("before") OffsetDateTime before);
    
    /**
     * Update reconciliation status
     */
    @Modifying
    @Query("UPDATE SnapshotEntity s SET s.reconciliationStatus = :status, " +
           "s.provisionalTradeId = :provisionalTradeId " +
           "WHERE s.positionKey = :positionKey")
    void updateReconciliationStatus(
            @Param("positionKey") String positionKey,
            @Param("status") ReconciliationStatus status,
            @Param("provisionalTradeId") String provisionalTradeId
    );
    
    /**
     * Find provisional snapshots older than threshold
     */
    @Query("SELECT s FROM SnapshotEntity s WHERE s.reconciliationStatus = :status " +
           "AND s.lastUpdatedAt < :threshold")
    List<SnapshotEntity> findStaleProvisionalSnapshots(
            @Param("status") ReconciliationStatus status,
            @Param("threshold") OffsetDateTime threshold
    );
}
