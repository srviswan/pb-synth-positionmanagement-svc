package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Find latest version for a position
     */
    @Query("SELECT MAX(e.eventVer) FROM EventEntity e WHERE e.positionKey = :positionKey")
    Optional<Long> findLatestVersion(@Param("positionKey") String positionKey);
    
    /**
     * Find events by position key ordered by version (for replay)
     */
    @Query("SELECT e FROM EventEntity e WHERE e.positionKey = :positionKey ORDER BY e.eventVer ASC")
    List<EventEntity> findByPositionKeyOrderByEventVer(@Param("positionKey") String positionKey);
    
    /**
     * Find events by position key and version range
     */
    @Query("SELECT e FROM EventEntity e WHERE e.positionKey = :positionKey " +
           "AND e.eventVer >= :fromVersion AND e.eventVer <= :toVersion " +
           "ORDER BY e.eventVer ASC")
    List<EventEntity> findByPositionKeyAndVersionRange(
            @Param("positionKey") String positionKey,
            @Param("fromVersion") Long fromVersion,
            @Param("toVersion") Long toVersion
    );
    
    /**
     * Find events by position key and effective date range (for coldpath insertion point)
     */
    @Query("SELECT e FROM EventEntity e WHERE e.positionKey = :positionKey " +
           "AND e.effectiveDate >= :fromDate AND e.effectiveDate <= :toDate " +
           "ORDER BY e.effectiveDate ASC, e.eventVer ASC")
    List<EventEntity> findByPositionKeyAndEffectiveDateRange(
            @Param("positionKey") String positionKey,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
    
    /**
     * Find events by correlation ID (for tracing)
     */
    @Query("SELECT e FROM EventEntity e WHERE e.correlationId = :correlationId ORDER BY e.occurredAt ASC")
    List<EventEntity> findByCorrelationId(@Param("correlationId") String correlationId);
}
