package com.bank.esps.domain.cdm.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Currency amount. Aligns with CDM {@code Money}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Money {
    private BigDecimal value;
    private String currency;

    public static Money of(BigDecimal value, String currency) {
        return Money.builder().value(value).currency(currency).build();
    }

    public static Money zero(String currency) {
        return of(BigDecimal.ZERO, currency);
    }

    public BigDecimal amount() {
        return value != null ? value : BigDecimal.ZERO;
    }

    public Money plus(Money other) {
        return Money.builder()
                .value(amount().add(other == null ? BigDecimal.ZERO : other.amount()))
                .currency(currency)
                .build();
    }
}
