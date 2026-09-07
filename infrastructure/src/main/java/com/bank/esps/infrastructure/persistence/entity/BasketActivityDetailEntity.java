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
import java.time.OffsetDateTime;

@Entity
@Table(name = "basket_activity_detail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketActivityDetailEntity {

    @Id
    @Column(name = "detail_id", length = 64)
    private String detailId;

    @Column(name = "activity_id", nullable = false, length = 64)
    private String activityId;

    @Column(name = "trade_id", nullable = false, length = 255)
    private String tradeId;

    @Column(name = "underlier_id", length = 255)
    private String underlierId;

    @Column(name = "quantity", nullable = false, precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(name = "price", nullable = false, precision = 28, scale = 8)
    private BigDecimal price;

    @Column(name = "currency", length = 16)
    private String currency;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "allocation_status", nullable = false, length = 32)
    private String allocationStatus;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;
}
