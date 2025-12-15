package com.bank.esps.infrastructure.persistence.entity;

import com.bank.esps.domain.enums.PositionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * UPI History Entity
 * Tracks all UPI changes for audit purposes
 */
@Entity
@Table(name = "upi_history", indexes = {
    @Index(name = "idx_upi_history_position_key", columnList = "position_key"),
    @Index(name = "idx_upi_history_upi", columnList = "upi"),
    @Index(name = "idx_upi_history_occurred_at", columnList = "occurred_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UPIHistoryEntity {
    
    @Id
    @Column(name = "history_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID historyId;
    
    @Column(name = "position_key", length = 64, nullable = false)
    private String positionKey;
    
    @Column(name = "upi", length = 128, nullable = false)
    private String upi;
    
    @Column(name = "previous_upi", length = 128)
    private String previousUPI;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PositionStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private PositionStatus previousStatus;
    
    @Column(name = "change_type", length = 50, nullable = false)
    private String changeType; // CREATED, TERMINATED, REOPENED, INVALIDATED, MERGED, RESTORED
    
    @Column(name = "triggering_trade_id", length = 128)
    private String triggeringTradeId;
    
    @Column(name = "backdated_trade_id", length = 128)
    private String backdatedTradeId; // If change was caused by backdated trade
    
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;
    
    @Column(name = "effective_date", nullable = false)
    private java.time.LocalDate effectiveDate;
    
    @Column(name = "reason", length = 255)
    private String reason; // Human-readable reason for the change
    
    @Column(name = "merged_from_position_key", length = 64)
    private String mergedFromPositionKey; // If this is a merge, track source position
    
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
}
