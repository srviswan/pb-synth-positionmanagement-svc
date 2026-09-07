package com.bank.esps.domain.cdm.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Product underlying a position. Aligns with CDM {@code Product} /
 * {@code NonTransferableProduct} used on {@code Position} and
 * {@code TradableProduct}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private Instrument instrument;
    private String currency;
    private String productQualifier;
    private String contractId;

    public static Product equity(String instrumentId, String currency) {
        return Product.builder()
                .instrument(Instrument.of(instrumentId))
                .currency(currency)
                .productQualifier("Equity")
                .build();
    }

    public String instrumentId() {
        return instrument != null ? instrument.getInstrumentId() : null;
    }
}
