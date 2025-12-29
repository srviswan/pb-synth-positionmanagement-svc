package com.bank.esps.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Domain event representing a trade
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeEvent {
    private String tradeId;
    private String account;
    private String instrument;
    private String currency;
    private BigDecimal quantity;
    private BigDecimal price;
    private LocalDate tradeDate;
    private LocalDate effectiveDate;
    private LocalDate settlementDate;
    private String positionKey;
    private String contractId; // Contract ID for tax lot method rules
}
