package com.bank.esps.infrastructure.persistence.entity.state;

import com.bank.esps.domain.state.LotStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "sm_lot_daily")
@IdClass(SmLotDailyEntity.Key.class)
@Getter
@Setter
public class SmLotDailyEntity {
    @Id
    @Column(name = "position_key", length = 64)
    private String positionKey;

    @Id
    @Column(name = "lot_id", length = 64)
    private String lotId;

    @Id
    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "region", nullable = false, length = 32)
    private String region;

    @Column(name = "remaining_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal remainingQty;

    @Column(name = "tradeable_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal tradeableQty;

    @Column(name = "original_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal originalQty;

    @Column(name = "open_price", nullable = false, precision = 28, scale = 8)
    private BigDecimal openPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LotStatus status;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "realized_pnl", nullable = false, precision = 28, scale = 8)
    private BigDecimal realizedPnl;

    public static class Key implements java.io.Serializable {
        public String positionKey;
        public String lotId;
        public LocalDate businessDate;

        public Key() {
        }

        public Key(String positionKey, String lotId, LocalDate businessDate) {
            this.positionKey = positionKey;
            this.lotId = lotId;
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
                    && java.util.Objects.equals(lotId, key.lotId)
                    && java.util.Objects.equals(businessDate, key.businessDate);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(positionKey, lotId, businessDate);
        }
    }
}
