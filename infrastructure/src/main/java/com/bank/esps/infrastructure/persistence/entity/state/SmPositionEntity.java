package com.bank.esps.infrastructure.persistence.entity.state;

import com.bank.esps.domain.state.LifecycleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sm_position")
@Getter
@Setter
public class SmPositionEntity {
    @Id
    @Column(name = "position_key", length = 64)
    private String positionKey;

    @Column(name = "region", nullable = false, length = 32)
    private String region;

    @Column(name = "account", nullable = false, length = 64)
    private String account;

    @Column(name = "instrument", nullable = false, length = 64)
    private String instrument;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LifecycleStatus status;

    @Column(name = "upi", length = 128)
    private String upi;

    @Column(name = "total_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal totalQty;

    @Column(name = "avg_price", nullable = false, precision = 28, scale = 8)
    private BigDecimal avgPrice;

    @Column(name = "open_lot_count", nullable = false)
    private int openLotCount;

    @Column(name = "realized_pnl", nullable = false, precision = 28, scale = 8)
    private BigDecimal realizedPnl;

    @Column(name = "as_of_date")
    private LocalDate asOfDate;

    @Column(name = "next_lot_seq", nullable = false)
    private long nextLotSeq;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
