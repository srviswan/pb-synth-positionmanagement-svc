package com.bank.esps.domain.cdm.event;

import com.bank.esps.domain.cdm.position.CounterpartyPositionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Instruction applied to a before position state. Aligns with CDM
 * {@code Instruction}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instruction {
    private PrimitiveInstruction primitiveInstruction;
    private CounterpartyPositionState before;
}
