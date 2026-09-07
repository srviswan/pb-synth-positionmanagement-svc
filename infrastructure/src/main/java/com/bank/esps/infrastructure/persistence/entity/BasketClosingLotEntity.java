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
@Table(name = "basket_closing_lot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketClosingLotEntity {

    @Id
    @Column(name = "closing_lot_id", length = 64)
    private String closingLotId;

    @Column(name = "activity_id", nullable = false, length = 64)
    private String activityId;

    @Column(name = "opened_lot_id", nullable = false, length = 64)
    private String openedLotId;

    @Column(name = "closing_detail_id", length = 64)
    private String closingDetailId;

    @Column(name = "closing_trade_id", length = 255)
    private String closingTradeId;

    @Column(name = "closed_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal closedQty;

    @Column(name = "close_price", nullable = false, precision = 28, scale = 8)
    private BigDecimal closePrice;

    @Column(name = "cost_basis", precision = 28, scale = 8)
    private BigDecimal costBasis;

    @Column(name = "realized_pnl", nullable = false, precision = 28, scale = 8)
    private BigDecimal realizedPnl;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;
}
