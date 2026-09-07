package com.bank.esps.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "basket_settlement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketSettlementEntity {

    @Id
    @Column(name = "settlement_id", length = 64)
    private String settlementId;

    @Column(name = "activity_id", nullable = false, length = 64)
    private String activityId;

    @Column(name = "detail_id", nullable = false, length = 64)
    private String detailId;

    @Column(name = "trade_id", nullable = false, length = 255)
    private String tradeId;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "settled_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal settledQty;

    @Column(name = "currency", length = 16)
    private String currency;

    @Column(name = "status", nullable = false, length = 32)
    private String status;
}
