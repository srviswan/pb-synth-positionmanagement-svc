package com.bank.esps.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reconciliation break entity
 * Tracks discrepancies between internal and external positions
 */
@Entity
@Table(name = "reconciliation_breaks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationBreakEntity {
    
    @Id
    @Column(name = "break_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID breakId;
    
    @Column(name = "position_key", length = 64, nullable = false)
    private String positionKey;
    
    @Column(name = "break_type", length = 50, nullable = false)
    private String breakType; // QUANTITY_MISMATCH, PRICE_MISMATCH, etc.
    
    @Column(name = "severity", length = 20, nullable = false)
    private String severity; // CRITICAL, WARNING, INFO
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "internal_value", columnDefinition = "jsonb")
    private String internalValue;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_value", columnDefinition = "jsonb")
    private String externalValue;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regulatory_value", columnDefinition = "jsonb")
    private String regulatoryValue;
    
    @Column(name = "detected_at", nullable = false, updatable = false)
    private OffsetDateTime detectedAt;
    
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
    
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "OPEN"; // OPEN, INVESTIGATING, RESOLVED
    
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
    
    @Column(name = "assigned_to", length = 64)
    private String assignedTo;
    
    @Column(name = "archival_flag", nullable = false)
    @Builder.Default
    private Boolean archivalFlag = false;
    
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;
    
    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = OffsetDateTime.now();
        }
        if (archivalFlag == null) {
            archivalFlag = false;
        }
    }
}
