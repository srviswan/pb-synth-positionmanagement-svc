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
@Table(name = "basket_div_open_lot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketDivOpenLotEntity {

    @Id
    @Column(name = "lot_id", length = 64)
    private String lotId;

    @Column(name = "activity_id", nullable = false, length = 64)
    private String activityId;

    @Column(name = "source_open_lot_id", length = 64)
    private String sourceOpenLotId;

    @Column(name = "dividend_id", nullable = false, length = 255)
    private String dividendId;

    @Column(name = "ex_date")
    private LocalDate exDate;

    @Column(name = "pay_date")
    private LocalDate payDate;

    @Column(name = "quantity", nullable = false, precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(name = "remaining_qty", nullable = false, precision = 28, scale = 8)
    private BigDecimal remainingQty;

    @Column(name = "rate", precision = 28, scale = 8)
    private BigDecimal rate;

    @Column(name = "amount", precision = 28, scale = 8)
    private BigDecimal amount;

    @Column(name = "currency", length = 16)
    private String currency;
}
