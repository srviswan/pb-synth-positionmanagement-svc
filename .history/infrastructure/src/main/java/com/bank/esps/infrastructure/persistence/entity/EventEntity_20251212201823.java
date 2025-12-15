package com.bank.esps.infrastructure.persistence.entity;

import com.bank.esps.domain.enums.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Event entity for the partitioned event store
 * Represents an immutable event in the event sourcing pattern
 */
@Entity
@Table(name = "event_store")
@IdClass(EventEntity.EventEntityId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEntity {
    
    @Id
    @Column(name = "position_key", length = 64, nullable = false)
    private String positionKey;
    
    @Id
    @Column(name = "event_ver", nullable = false)
    private Long eventVer;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 30, nullable = false)
    private EventType eventType;
    
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
    
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload; // JSON string
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta_lots", columnDefinition = "jsonb")
    private String metaLots; // JSON string for lot allocations
    
    @Column(name = "correlation_id", length = 128)
    private String correlationId;
    
    @Column(name = "causation_id", length = 128)
    private String causationId;
    
    @Column(name = "contract_id", length = 64)
    private String contractId;
    
    @Column(name = "user_id", length = 64)
    private String userId;
    
    @Column(name = "archival_flag", nullable = false)
    @Builder.Default
    private Boolean archivalFlag = false;
    
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;
    
    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }
        if (archivalFlag == null) {
            archivalFlag = false;
        }
    }
    
    /**
     * Composite key for EventEntity
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventEntityId implements java.io.Serializable {
        private String positionKey;
        private Long eventVer;
    }
}
