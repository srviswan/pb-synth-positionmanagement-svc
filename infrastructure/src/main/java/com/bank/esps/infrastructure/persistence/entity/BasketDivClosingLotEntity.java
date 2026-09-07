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
@Table(name = "basket_div_closing_lot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketDivClosingLotEntity {

    @Id
    @Column(name = "closing_lot_id", length = 64)
    private String closingLotId;

    @Column(name = "activity_id", nullable = false, length = 64)
    private String activityId;

    @Column(name = "opened_dividend_lot_id", nullable = false, length = 64)
    private String openedDividendLotId;

    @Column(name = "dividend_id", nullable = false, length = 255)
    private String dividendId;

    @Column(name = "closed_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal closedQty;

    @Column(name = "amount", precision = 28, scale = 8)
    private BigDecimal amount;

    @Column(name = "pay_date")
    private LocalDate payDate;

    @Column(name = "currency", length = 16)
    private String currency;
}
