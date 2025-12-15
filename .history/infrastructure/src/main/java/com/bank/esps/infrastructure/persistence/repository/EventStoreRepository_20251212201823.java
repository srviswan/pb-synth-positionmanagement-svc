package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for event store operations
 * Handles partitioned event store queries
 */
@Repository
public interface EventStoreRepository extends JpaRepository<EventEntity, EventEntity.EventEntityId> {
    
    /**
     * Find latest version for a position (excluding archived)
     */
    @Query("SELECT MAX(e.eventVer) FROM EventEntity e WHERE e.positionKey = :positionKey " +
           "AND e.archivalFlag = false")
    Optional<Long> findLatestVersion(@Param("positionKey") String positionKey);
    
    /**
     * Find events by position key ordered by version (for replay)
     * Excludes archived events by default
     */
    @Query("SELECT e FROM EventEntity e WHERE e.positionKey = :positionKey " +
           "AND e.archivalFlag = false ORDER BY e.eventVer ASC")
    List<EventEntity> findByPositionKeyOrderByEventVer(@Param("positionKey") String positionKey);
    
    /**
     * Find events by position key ordered by version (including archived)
     * For historical queries or compliance purposes
     */
    @Query("SELECT e FROM EventEntity e WHERE e.positionKey = :positionKey ORDER BY e.eventVer ASC")
    List<EventEntity> findByPositionKeyOrderByEventVerIncludingArchived(@Param("positionKey") String positionKey);
    
    /**
     * Find events by position key and version range
     * Excludes archived events by default
     */
    @Query("SELECT e FROM EventEntity e WHERE e.positionKey = :positionKey " +
           "AND e.eventVer >= :fromVersion AND e.eventVer <= :toVersion " +
           "AND e.archivalFlag = false " +
           "ORDER BY e.eventVer ASC")
    List<EventEntity> findByPositionKeyAndVersionRange(
            @Param("positionKey") String positionKey,
            @Param("fromVersion") Long fromVersion,
            @Param("toVersion") Long toVersion
    );
    
    /**
     * Find events by position key and effective date range (for coldpath insertion point)
     * Orders by effective date, then occurredAt timestamp, then version for consistent ordering
     * Excludes archived events by default
     */
    @Query("SELECT e FROM EventEntity e WHERE e.positionKey = :positionKey " +
           "AND e.effectiveDate >= :fromDate AND e.effectiveDate <= :toDate " +
           "AND e.archivalFlag = false " +
           "ORDER BY e.effectiveDate ASC, e.occurredAt ASC, e.eventVer ASC")
    List<EventEntity> findByPositionKeyAndEffectiveDateRange(
            @Param("positionKey") String positionKey,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
    
    /**
     * Find events by correlation ID (for tracing)
     * Excludes archived events by default
     */
    @Query("SELECT e FROM EventEntity e WHERE e.correlationId = :correlationId " +
           "AND e.archivalFlag = false ORDER BY e.occurredAt ASC")
    List<EventEntity> findByCorrelationId(@Param("correlationId") String correlationId);
    
    // ========== Archival Methods ==========
    
    /**
     * Find archived events by partition number
     * Used for archival export operations
     */
    @Query(value = "SELECT * FROM event_store " +
                    "WHERE hashtext(position_key) % 16 = :partitionNumber " +
                    "AND archival_flag = true " +
                    "ORDER BY effective_date, event_ver", nativeQuery = true)
    List<EventEntity> findArchivedEventsByPartition(@Param("partitionNumber") int partitionNumber);
    
    /**
     * Count events ready for archival (older than cutoff date, not yet archived)
     */
    @Query("SELECT COUNT(e) FROM EventEntity e WHERE e.effectiveDate < :cutoffDate " +
           "AND e.archivalFlag = false")
    long countEventsReadyForArchival(@Param("cutoffDate") LocalDate cutoffDate);
    
    /**
     * Mark partition for archival (updates archival_flag and archived_at)
     * Note: This requires native query for partition-based update
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE event_store " +
                   "SET archival_flag = true, archived_at = CURRENT_TIMESTAMP " +
                   "WHERE hashtext(position_key) % 16 = :partitionNumber " +
                   "AND effective_date < :cutoffDate " +
                   "AND archival_flag = false", nativeQuery = true)
    int markPartitionForArchival(@Param("partitionNumber") int partitionNumber, 
                                 @Param("cutoffDate") LocalDate cutoffDate);
}
