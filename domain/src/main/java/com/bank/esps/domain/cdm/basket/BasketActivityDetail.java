package com.bank.esps.domain.cdm.basket;

import com.bank.esps.domain.cdm.product.Underlier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Hedge trade allocated to a contract. Maps to the firm's
 * {@code DOBasketActivityDetails}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketActivityDetail {
    private String detailId;
    private String tradeId;
    private Underlier underlier;
    private BigDecimal quantity;
    private BigDecimal price;
    private String currency;
    private LocalDate tradeDate;
    private LocalDate effectiveDate;
    private LocalDate settlementDate;
    private String allocationStatus;
    private OffsetDateTime recordedAt;
}
