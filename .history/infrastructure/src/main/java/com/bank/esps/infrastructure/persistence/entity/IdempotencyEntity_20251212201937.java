package com.bank.esps.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Idempotency entity to prevent duplicate processing
 */
@Entity
@Table(name = "idempotency_store")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyEntity {
    
    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;
    
    @Column(name = "trade_id", length = 128, nullable = false, unique = true)
    private String tradeId;
    
    @Column(name = "position_key", length = 64, nullable = false)
    private String positionKey;
    
    @Column(name = "event_version")
    private Long eventVersion;
    
    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;
    
    @Column(name = "status", length = 20, nullable = false)
    private String status; // PROCESSED, FAILED
    
    @Column(name = "correlation_id", length = 128)
    private String correlationId;
    
    @Column(name = "archival_flag", nullable = false)
    @Builder.Default
    private Boolean archivalFlag = false;
    
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;
    
    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = OffsetDateTime.now();
        }
        if (archivalFlag == null) {
            archivalFlag = false;
        }
    }
}
