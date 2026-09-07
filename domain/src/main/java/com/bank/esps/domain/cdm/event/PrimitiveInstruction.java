package com.bank.esps.domain.cdm.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Primitive inputs applied to a before state. Aligns with CDM
 * {@code PrimitiveInstruction}. This service uses quantity change as the
 * primary primitive for position keeping.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrimitiveInstruction {
    private QuantityChangeInstruction quantityChange;

    public static PrimitiveInstruction quantityChange(QuantityChangeInstruction instruction) {
        return PrimitiveInstruction.builder().quantityChange(instruction).build();
    }
}
