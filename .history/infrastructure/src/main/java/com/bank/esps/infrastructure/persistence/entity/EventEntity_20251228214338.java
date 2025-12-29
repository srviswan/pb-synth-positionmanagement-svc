package com.bank.esps.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * JPA entity for event store
 */
@Entity
@Table(name = "event_store", indexes = {
    @Index(name = "idx_event_store_position_key", columnList = "position_key"),
    @Index(name = "idx_event_store_effective_date", columnList = "effective_date"),
    @Index(name = "idx_event_store_occurred_at", columnList = "occurred_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "position_key", nullable = false, length = 255)
    private String positionKey;
    
    @Column(name = "event_ver", nullable = false)
    private Integer eventVer;
    
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    
    @Column(name = "event_data", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    @JdbcTypeCode(SqlTypes.JSON)
    private String eventData;
    
    @Column(name = "effective_date", nullable = false)
    private java.time.LocalDate effectiveDate;
    
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
    
    @Column(name = "correlation_id", length = 255)
    private String correlationId;
    
    @Column(name = "causation_id", length = 255)
    private String causationId;
    
    @Column(name = "contract_id", length = 64)
    private String contractId;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
