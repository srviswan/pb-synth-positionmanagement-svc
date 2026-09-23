package com.bank.esps.infrastructure.persistence.entity.state;

import com.bank.esps.domain.state.LifecycleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "sm_position_daily")
@IdClass(SmPositionDailyEntity.Key.class)
@Getter
@Setter
public class SmPositionDailyEntity {
    @Id
    @Column(name = "position_key", length = 64)
    private String positionKey;

    @Id
    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "region", nullable = false, length = 32)
    private String region;

    @Column(name = "total_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal totalQty;

    @Column(name = "avg_price", nullable = false, precision = 28, scale = 8)
    private BigDecimal avgPrice;

    @Column(name = "open_lot_count", nullable = false)
    private int openLotCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LifecycleStatus status;

    @Column(name = "upi", length = 128)
    private String upi;

    @Column(name = "realized_pnl", nullable = false, precision = 28, scale = 8)
    private BigDecimal realizedPnl;

    public static class Key implements java.io.Serializable {
        public String positionKey;
        public LocalDate businessDate;

        public Key() {
        }

        public Key(String positionKey, LocalDate businessDate) {
            this.positionKey = positionKey;
            this.businessDate = businessDate;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return java.util.Objects.equals(positionKey, key.positionKey)
                    && java.util.Objects.equals(businessDate, key.businessDate);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(positionKey, businessDate);
        }
    }
}
