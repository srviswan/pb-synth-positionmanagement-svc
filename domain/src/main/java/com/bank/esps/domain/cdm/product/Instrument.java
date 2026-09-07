package com.bank.esps.domain.cdm.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Identifiable underlier. Aligns with CDM {@code Security} /
 * {@code ProductIdentifier} for equity and similar cash instruments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instrument {
    private String instrumentId;
    private String description;
    private String assetClass;

    public static Instrument of(String instrumentId) {
        return Instrument.builder()
                .instrumentId(instrumentId)
                .assetClass("EQUITY")
                .build();
    }
}
