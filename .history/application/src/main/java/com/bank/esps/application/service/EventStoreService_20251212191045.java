package com.bank.esps.application.service;

import com.bank.esps.domain.enums.EventType;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.LotAllocationResult;
import com.bank.esps.infrastructure.persistence.entity.EventEntity;
import com.bank.esps.infrastructure.persistence.repository.EventStoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing event store operations
 * Handles event creation, persistence, and retrieval
 */
@Service
public class EventStoreService {
    
    private static final Logger log = LoggerFactory.getLogger(EventStoreService.class);
    
    private final EventStoreRepository eventStoreRepository;
    private final ObjectMapper objectMapper;
    
    public EventStoreService(EventStoreRepository eventStoreRepository, ObjectMapper objectMapper) {
        this.eventStoreRepository = eventStoreRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Create event entity from trade event
     */
    public EventEntity createEventEntity(TradeEvent trade, long version, LotAllocationResult result) {
        try {
            EventEntity event = new EventEntity();
            event.setPositionKey(trade.getPositionKey());
            event.setEventVer(version);
            event.setEventType(EventType.valueOf(trade.getTradeType()));
            event.setEffectiveDate(trade.getEffectiveDate());
            event.setOccurredAt(OffsetDateTime.now());
            event.setPayload(toJson(trade));
            event.setMetaLots(toJson(result.getAllocationsMap()));
            event.setCorrelationId(trade.getCorrelationId());
            event.setCausationId(trade.getCausationId());
            event.setContractId(trade.getContractId());
            event.setUserId(trade.getUserId());
            return event;
        } catch (Exception e) {
            log.error("Error creating event entity", e);
            throw new RuntimeException("Failed to create event entity", e);
        }
    }
    
    /**
     * Create event entity from trade event (for backdated trades)
     * Sets occurredAt to start of effective date for proper chronological ordering
     */
    public EventEntity createEventFromTrade(TradeEvent trade, long version) {
        EventEntity event = new EventEntity();
        event.setPositionKey(trade.getPositionKey());
        event.setEventVer(version);
        event.setEventType(EventType.valueOf(trade.getTradeType()));
        event.setEffectiveDate(trade.getEffectiveDate());
        
        // For backdated trades, set occurredAt to start of effective date (midnight)
        // This ensures backdated trades are processed before same-date events
        OffsetDateTime occurredAt;
        if (trade.getSequenceStatus() != null && 
            trade.getSequenceStatus().name().equals("BACKDATED")) {
            occurredAt = trade.getEffectiveDate().atStartOfDay()
                    .atOffset(java.time.ZoneOffset.UTC);
        } else {
            occurredAt = OffsetDateTime.now();
        }
        
        event.setOccurredAt(occurredAt);
        
        try {
            event.setPayload(toJson(trade));
        } catch (Exception e) {
            log.error("Error serializing trade event to JSON", e);
            throw new RuntimeException("Failed to serialize trade event", e);
        }
        
        event.setCorrelationId(trade.getCorrelationId());
        event.setCausationId(trade.getCausationId());
        event.setContractId(trade.getContractId());
        event.setUserId(trade.getUserId());
        
        return event;
    }
    
    /**
     * Save event to event store
     * Throws DataIntegrityViolationException on version conflicts (for retry)
     */
    @Transactional
    public EventEntity saveEvent(EventEntity event) {
        try {
            EventEntity savedEvent = eventStoreRepository.save(event);
            log.info("✅ Saved event for position {}: version {}, type {}, effectiveDate {}, payload length: {}", 
                    event.getPositionKey(), savedEvent.getEventVer(), savedEvent.getEventType(), 
                    savedEvent.getEffectiveDate(), 
                    savedEvent.getPayload() != null ? savedEvent.getPayload().length() : 0);
            return savedEvent;
        } catch (DataIntegrityViolationException e) {
            // Concurrency detected: retry
            log.warn("Version conflict for position {}, retrying", event.getPositionKey(), e);
            throw e;
        } catch (Exception e) {
            log.error("❌ CRITICAL: Error saving event for position {}: {}", event.getPositionKey(), e.getMessage(), e);
            log.error("Event details: positionKey={}, eventVer={}, eventType={}, payload length={}", 
                    event.getPositionKey(), event.getEventVer(), event.getEventType(),
                    event.getPayload() != null ? event.getPayload().length() : 0);
            throw new RuntimeException("Failed to save event for position " + event.getPositionKey(), e);
        }
    }
    
    /**
     * Load event stream for a position
     */
    public List<EventEntity> loadEventStream(String positionKey) {
        return eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
    }
    
    /**
     * Find event by position key and version
     */
    public Optional<EventEntity> findEvent(String positionKey, long version) {
        return eventStoreRepository.findById(
                new EventEntity.EventEntityId(positionKey, version));
    }
    
    /**
     * Check if event exists for a trade ID
     */
    public boolean eventExistsForTrade(String positionKey, String tradeId) {
        List<EventEntity> events = eventStoreRepository.findByPositionKeyOrderByEventVer(positionKey);
        return events.stream().anyMatch(event -> {
            try {
                TradeEvent tradeEvent = objectMapper.readValue(event.getPayload(), TradeEvent.class);
                return tradeId.equals(tradeEvent.getTradeId());
            } catch (Exception e) {
                return false;
            }
        });
    }
    
    /**
     * Convert object to JSON string
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Error converting object to JSON", e);
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
