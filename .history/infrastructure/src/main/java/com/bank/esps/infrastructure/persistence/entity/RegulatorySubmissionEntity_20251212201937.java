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
 * Regulatory submission entity
 * Tracks all regulatory submissions
 */
@Entity
@Table(name = "regulatory_submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulatorySubmissionEntity {
    
    @Id
    @Column(name = "submission_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID submissionId;
    
    @Column(name = "trade_id", length = 128, nullable = false)
    private String tradeId;
    
    @Column(name = "position_key", length = 64, nullable = false)
    private String positionKey;
    
    @Column(name = "submission_type", length = 50, nullable = false)
    private String submissionType; // TRADE_REPORT, POSITION_REPORT, etc.
    
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private OffsetDateTime submittedAt;
    
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING"; // PENDING, SUBMITTED, ACCEPTED, REJECTED
    
    @Column(name = "response_received_at")
    private OffsetDateTime responseReceivedAt;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "correlation_id", length = 128)
    private String correlationId;
    
    @Column(name = "archival_flag", nullable = false)
    @Builder.Default
    private Boolean archivalFlag = false;
    
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;
    
    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = OffsetDateTime.now();
        }
        if (archivalFlag == null) {
            archivalFlag = false;
        }
    }
}
