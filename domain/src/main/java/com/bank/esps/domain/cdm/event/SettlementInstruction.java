package com.bank.esps.domain.cdm.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Settlement status/quantity update against a Position's sibling settlement rows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementInstruction {
    private String detailId;
    private String tradeId;
    private LocalDate settlementDate;
    private BigDecimal settledQuantity;
    private String currency;
    @Builder.Default
    private String status = "SETTLED";
}
