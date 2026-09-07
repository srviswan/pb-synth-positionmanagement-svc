package com.bank.esps.domain.cdm.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Legal entity or person involved in a position. Aligns with CDM {@code Party}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Party {
    private String partyId;
    private String name;
    private String lei;

    public static Party of(String partyId) {
        return Party.builder().partyId(partyId).build();
    }
}
