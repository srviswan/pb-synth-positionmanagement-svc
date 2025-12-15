package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.domain.enums.PositionStatus;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    
    /**
     * Find positions by account with pagination
     */
    Page<SnapshotEntity> findByAccountAndArchivalFlagFalse(String account, Pageable pageable);
    
    /**
     * Find positions by account and status with pagination
     */
    Page<SnapshotEntity> findByAccountAndStatusAndArchivalFlagFalse(
            String account, PositionStatus status, Pageable pageable);
    
    /**
     * Find positions by instrument with pagination
     */
    Page<SnapshotEntity> findByInstrumentAndArchivalFlagFalse(String instrument, Pageable pageable);
    
    /**
     * Find positions by account and instrument with pagination
     */
    Page<SnapshotEntity> findByAccountAndInstrumentAndArchivalFlagFalse(
            String account, String instrument, Pageable pageable);
    
    /**
     * Find positions by account, instrument, and currency with pagination
     */
    Page<SnapshotEntity> findByAccountAndInstrumentAndCurrencyAndArchivalFlagFalse(
            String account, String instrument, String currency, Pageable pageable);
    
    /**
     * Find positions by contract ID with pagination
     */
    Page<SnapshotEntity> findByContractIdAndArchivalFlagFalse(String contractId, Pageable pageable);
    
    /**
     * Find positions by account with optional status filter and pagination
     */
    @Query("SELECT s FROM SnapshotEntity s WHERE s.account = :account " +
           "AND (s.archivalFlag = false OR s.archivalFlag IS NULL) " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<SnapshotEntity> findByAccountWithOptionalStatus(
            @Param("account") String account,
            @Param("status") PositionStatus status,
            Pageable pageable);
}
