package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EventStoreRepository extends JpaRepository<EventEntity, Long> {
    
    @Query("SELECT e FROM EventEntity e WHERE e.positionKey = :positionKey ORDER BY e.effectiveDate ASC, e.occurredAt ASC, e.eventVer ASC")
    List<EventEntity> findByPositionKeyOrderByEventVerAsc(@Param("positionKey") String positionKey);
    
    @Query("SELECT e FROM EventEntity e WHERE e.positionKey = :positionKey AND e.effectiveDate <= :asOfDate ORDER BY e.effectiveDate ASC, e.occurredAt ASC, e.eventVer ASC")
    List<EventEntity> findByPositionKeyAndEffectiveDateLessThanEqualOrderByEventVerAsc(
            @Param("positionKey") String positionKey,
            @Param("asOfDate") LocalDate asOfDate);
    
    @Query("SELECT MAX(e.eventVer) FROM EventEntity e WHERE e.positionKey = :positionKey")
    Integer findMaxVersionByPositionKey(@Param("positionKey") String positionKey);
}
