package com.bank.esps.domain.cdm.position;

import com.bank.esps.domain.cdm.base.Money;
import com.bank.esps.domain.cdm.base.Price;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Mark-to-market or model valuation. Aligns with CDM {@code Valuation}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Valuation {
    private Money amount;
    private ZonedDateTime timestamp;
    private String method;
    private String source;
    private Price priceComponent;
}
