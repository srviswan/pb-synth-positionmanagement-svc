package com.bank.esps.domain.cdm.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Measured quantity. Aligns with CDM {@code Quantity} / {@code NonNegativeQuantity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quantity {
    private BigDecimal value;
    private UnitType unit;
    private String currency;

    public static Quantity of(BigDecimal value, UnitType unit) {
        return Quantity.builder().value(value).unit(unit).build();
    }

    public static Quantity ofShares(BigDecimal value) {
        return of(value, UnitType.SHARES);
    }

    public BigDecimal amount() {
        return value != null ? value : BigDecimal.ZERO;
    }

    public Quantity plus(Quantity other) {
        return Quantity.builder()
                .value(amount().add(other == null ? BigDecimal.ZERO : other.amount()))
                .unit(unit)
                .currency(currency)
                .build();
    }

    public Quantity minus(Quantity other) {
        return Quantity.builder()
                .value(amount().subtract(other == null ? BigDecimal.ZERO : other.amount()))
                .unit(unit)
                .currency(currency)
                .build();
    }

    public Quantity abs() {
        return Quantity.builder()
                .value(amount().abs())
                .unit(unit)
                .currency(currency)
                .build();
    }
}
