package com.bank.esps.application.service;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for managing event store
 */
@Service
public class EventStoreService {
    
    private static final Logger log = LoggerFactory.getLogger(EventStoreService.class);
    
    private final EventStoreRepository eventStoreRepository;
    private final SnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;
    
    public EventStoreService(EventStoreRepository eventStoreRepository,
                           SnapshotRepository snapshotRepository,
                           ObjectMapper objectMapper) {
        this.eventStoreRepository = eventStoreRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public void appendEvent(String positionKey, TradeEvent event, int version) {
        appendEvent(positionKey, event, version, null, null);
    }
    
    @Transactional
    public void appendEvent(String positionKey, TradeEvent event, int version, 
                           String correlationId, String causationId) {
        try {
            // Get correlation ID from MDC if not provided
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = org.slf4j.MDC.get("correlationId");
            }
            
            EventEntity eventEntity = EventEntity.builder()
                    .positionKey(positionKey)
                    .eventVer(version)
                    .eventType("TRADE")
                    .eventData(objectMapper.writeValueAsString(event))
                    .effectiveDate(event.getEffectiveDate() != null ? event.getEffectiveDate() : event.getTradeDate())
                    .occurredAt(OffsetDateTime.now())
                    .correlationId(correlationId)
                    .causationId(causationId)
                    .createdAt(OffsetDateTime.now())
                    .build();
            
            eventStoreRepository.save(eventEntity);
            log.debug("Appended event for position: {}, version: {}, correlationId: {}", 
                    positionKey, version, correlationId);
        } catch (Exception e) {
            log.error("Failed to append event for position: {}", positionKey, e);
            throw new RuntimeException("Failed to append event", e);
        }
    }
    
    @Transactional(readOnly = true)
    public List<EventEntity> getEvents(String positionKey) {
        return eventStoreRepository.findByPositionKeyOrderByEventVerAsc(positionKey);
    }
    
    @Transactional(readOnly = true)
    public List<EventEntity> getEventsAsOf(String positionKey, LocalDate asOfDate) {
        return eventStoreRepository.findByPositionKeyAndEffectiveDateLessThanEqualOrderByEventVerAsc(positionKey, asOfDate);
    }
    
    @Transactional(readOnly = true)
    public int getNextVersion(String positionKey) {
        Integer maxVersion = eventStoreRepository.findMaxVersionByPositionKey(positionKey);
        return (maxVersion != null ? maxVersion : 0) + 1;
    }
    
    @Transactional
    public void saveSnapshot(String positionKey, PositionState state) {
        saveSnapshot(positionKey, state, com.bank.esps.domain.enums.ReconciliationStatus.RECONCILED, null);
    }
    
    @Transactional
    public void saveSnapshot(String positionKey, PositionState state, 
                            com.bank.esps.domain.enums.ReconciliationStatus reconciliationStatus, 
                            String provisionalTradeId) {
        try {
            String snapshotData = objectMapper.writeValueAsString(state);
            
            snapshotRepository.findByPositionKey(positionKey).ifPresentOrElse(
                    existing -> {
                        existing.setSnapshotData(snapshotData);
                        existing.setVersion(state.getVersion());
                        existing.setEffectiveDate(LocalDate.now());
                        existing.setUpdatedAt(OffsetDateTime.now());
                        existing.setReconciliationStatus(reconciliationStatus.name());
                        existing.setProvisionalTradeId(provisionalTradeId);
                        snapshotRepository.save(existing);
                    },
                    () -> {
                        var snapshot = com.bank.esps.infrastructure.persistence.entity.SnapshotEntity.builder()
                                .positionKey(positionKey)
                                .snapshotData(snapshotData)
                                .version(state.getVersion())
                                .effectiveDate(LocalDate.now())
                                .createdAt(OffsetDateTime.now())
                                .updatedAt(OffsetDateTime.now())
                                .reconciliationStatus(reconciliationStatus.name())
                                .provisionalTradeId(provisionalTradeId)
                                .build();
                        snapshotRepository.save(snapshot);
                    }
            );
            log.debug("Saved snapshot for position: {}, status: {}", positionKey, reconciliationStatus);
        } catch (Exception e) {
            log.error("Failed to save snapshot for position: {}", positionKey, e);
            throw new RuntimeException("Failed to save snapshot", e);
        }
    }
    
    @Transactional(readOnly = true)
    public PositionState loadSnapshot(String positionKey) {
        return snapshotRepository.findByPositionKey(positionKey)
                .map(snapshot -> {
                    try {
                        return objectMapper.readValue(snapshot.getSnapshotData(), PositionState.class);
                    } catch (Exception e) {
                        log.error("Failed to deserialize snapshot for position: {}", positionKey, e);
                        return null;
                    }
                })
                .orElse(null);
    }
}
