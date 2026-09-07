package com.bank.esps.domain.cdm.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Price of an asset or cash fee. Aligns with CDM {@code Price}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Price {
    private BigDecimal value;
    private String currency;
    @Builder.Default
    private PriceType priceType = PriceType.ASSET_PRICE;
    private UnitType unitOfAmount;
    private UnitType perUnitOfAmount;

    public static Price assetPrice(BigDecimal value, String currency) {
        return Price.builder()
                .value(value)
                .currency(currency)
                .priceType(PriceType.ASSET_PRICE)
                .unitOfAmount(UnitType.CURRENCY)
                .perUnitOfAmount(UnitType.SHARES)
                .build();
    }

    public BigDecimal amount() {
        return value != null ? value : BigDecimal.ZERO;
    }
}
