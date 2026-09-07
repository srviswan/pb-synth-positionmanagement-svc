package com.bank.esps.domain.cdm.position;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Qualification of a closed position. Aligns with CDM {@code ClosedState}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosedState {
    private ClosedStateReason reason;
    private LocalDate effectiveDate;

    public static ClosedState terminated(LocalDate effectiveDate) {
        return ClosedState.builder()
                .reason(ClosedStateReason.TERMINATED)
                .effectiveDate(effectiveDate)
                .build();
    }
}
