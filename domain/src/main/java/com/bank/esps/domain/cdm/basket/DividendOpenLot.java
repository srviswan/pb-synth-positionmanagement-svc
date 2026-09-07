package com.bank.esps.domain.cdm.basket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Open dividend lot on a Position. Maps to the firm's
 * {@code DOBasketActDivOpenLotTable}. CDM analogue is a corporate-action
 * {@code ObservationEvent} applied to lots.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendOpenLot {
    private String lotId;
    private String sourceOpenLotId;
    private String dividendId;
    private LocalDate exDate;
    private LocalDate payDate;
    private BigDecimal quantity;
    private BigDecimal remainingQuantity;
    private BigDecimal rate;
    private BigDecimal amount;
    private String currency;

    public BigDecimal remaining() {
        return remainingQuantity != null ? remainingQuantity : BigDecimal.ZERO;
    }
}
