package com.bank.esps.infrastructure.persistence.entity.state;

import com.bank.esps.domain.state.LotStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "sm_lot")
@Getter
@Setter
public class SmLotEntity {
    @Id
    @Column(name = "lot_id", length = 64)
    private String lotId;

    @Column(name = "position_key", nullable = false, length = 64)
    private String positionKey;

    @Column(name = "parent_lot_id", length = 64)
    private String parentLotId;

    @Column(name = "opening_trade_id", nullable = false, length = 128)
    private String openingTradeId;

    @Column(name = "closing_trade_id", length = 128)
    private String closingTradeId;

    @Column(name = "original_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal originalQty;

    @Column(name = "remaining_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal remainingQty;

    @Column(name = "open_price", nullable = false, precision = 28, scale = 8)
    private BigDecimal openPrice;

    @Column(name = "close_price", precision = 28, scale = 8)
    private BigDecimal closePrice;

    @Column(name = "realized_pnl", nullable = false, precision = 28, scale = 8)
    private BigDecimal realizedPnl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LotStatus status;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "closed_on")
    private LocalDate closedOn;
}
