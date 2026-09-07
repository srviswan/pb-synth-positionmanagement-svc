package com.bank.esps.domain.cdm.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single assigned identifier value. Aligns with CDM
 * {@code AssignedIdentifier}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignedIdentifier {
    private String value;
    private Integer version;
}
