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
@Table(name = "basket_open_lot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketOpenLotEntity {

    @Id
    @Column(name = "lot_id", length = 64)
    private String lotId;

    @Column(name = "activity_id", nullable = false, length = 64)
    private String activityId;

    @Column(name = "source_detail_id", length = 64)
    private String sourceDetailId;

    @Column(name = "source_trade_id", length = 255)
    private String sourceTradeId;

    @Column(name = "original_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal originalQty;

    @Column(name = "remaining_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal remainingQty;

    @Column(name = "cost_basis", nullable = false, precision = 28, scale = 8)
    private BigDecimal costBasis;

    @Column(name = "current_ref_price", precision = 28, scale = 8)
    private BigDecimal currentRefPrice;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "settled_qty", precision = 28, scale = 8)
    private BigDecimal settledQty;

    @Column(name = "acquisition_sequence", nullable = false)
    private Integer acquisitionSequence;
}
