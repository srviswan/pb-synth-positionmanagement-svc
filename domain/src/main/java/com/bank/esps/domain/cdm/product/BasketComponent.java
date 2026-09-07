package com.bank.esps.domain.cdm.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Weighted constituent of a basket underlier. Aligns with CDM {@code Basket}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketComponent {
    private Underlier underlier;
    private BigDecimal weight;
}
