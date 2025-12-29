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
 * JPA entity for position snapshots
 */
@Entity
@Table(name = "snapshot", indexes = {
    @Index(name = "idx_snapshot_position_key", columnList = "position_key"),
    @Index(name = "idx_snapshot_effective_date", columnList = "effective_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "position_key", nullable = false, unique = true, length = 255)
    private String positionKey;
    
    @Column(name = "snapshot_data", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    @JdbcTypeCode(SqlTypes.JSON)
    private String snapshotData;
    
    @Column(name = "version", nullable = false)
    private Integer version;
    
    @Column(name = "effective_date", nullable = false)
    private java.time.LocalDate effectiveDate;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    
    @Column(name = "reconciliation_status", length = 20, nullable = false)
    private String reconciliationStatus = "RECONCILED";
    
    @Column(name = "provisional_trade_id", length = 255)
    private String provisionalTradeId;
    
    @Column(name = "contract_id", length = 64)
    private String contractId;
    
    @Column(name = "account", length = 255)
    private String account;
    
    @Column(name = "instrument", length = 255)
    private String instrument;
}
