package com.bank.esps.domain.cdm.basket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Settlement of an allocated hedge. Maps to the firm's
 * {@code DOBasketSettlementTable}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketSettlement {
    private String settlementId;
    private String detailId;
    private String tradeId;
    private LocalDate settlementDate;
    private BigDecimal settledQuantity;
    private String currency;
    private String status;
}
