package com.bank.esps.infrastructure.persistence.entity;

import com.bank.esps.domain.enums.PositionStatus;
import com.bank.esps.domain.enums.ReconciliationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Snapshot entity for the hot cache
 * Single row per position, overwritten on every event
 */
@Entity
@Table(name = "snapshot_store")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotEntity {
    
    @Id
    @Column(name = "position_key", length = 64)
    private String positionKey;
    
    @Column(name = "last_ver", nullable = false)
    private Long lastVer;
    
    @Column(name = "uti", length = 128, nullable = false)
    private String uti;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PositionStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", length = 20, nullable = false)
    @Builder.Default
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.RECONCILED;
    
    @Column(name = "provisional_trade_id", length = 128)
    private String provisionalTradeId;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tax_lots_compressed", nullable = false, columnDefinition = "jsonb")
    private String taxLotsCompressed; // JSON string
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_metrics", columnDefinition = "jsonb")
    private String summaryMetrics; // JSON string
    
    @Column(name = "last_updated_at", nullable = false, updatable = false)
    private OffsetDateTime lastUpdatedAt;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version; // For optimistic locking
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = OffsetDateTime.now();
    }
}
