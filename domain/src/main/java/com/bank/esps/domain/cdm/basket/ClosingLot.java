package com.bank.esps.domain.cdm.basket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Closed portion of a lot. Maps to the firm's {@code DOBasketActClosingLotTable}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosingLot {
    private String closingLotId;
    private String openedLotId;
    private String closingDetailId;
    private String closingTradeId;
    private BigDecimal closedQuantity;
    private BigDecimal closePrice;
    private BigDecimal costBasis;
    private BigDecimal realizedPnL;
    private LocalDate tradeDate;
    private LocalDate settlementDate;
}
