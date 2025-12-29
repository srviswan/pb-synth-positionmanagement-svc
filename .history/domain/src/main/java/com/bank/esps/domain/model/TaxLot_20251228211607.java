package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a tax lot within a position
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxLot {
    private String lotId;
    private BigDecimal originalQty;
    private BigDecimal remainingQty;
    private BigDecimal costBasis;
    private BigDecimal currentRefPrice;
    private LocalDate tradeDate;
    private LocalDate settlementDate;
    private BigDecimal settledQuantity;
}
