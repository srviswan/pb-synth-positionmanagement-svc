package com.bank.esps.domain.cdm.basket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Closed dividend lot on a Position. Maps to the firm's
 * {@code DOBasketActDivClosingTable}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendClosingLot {
    private String closingLotId;
    private String openedDividendLotId;
    private String dividendId;
    private BigDecimal closedQuantity;
    private BigDecimal amount;
    private LocalDate payDate;
    private String currency;
}
