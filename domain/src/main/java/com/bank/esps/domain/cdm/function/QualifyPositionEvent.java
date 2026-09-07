package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.event.PositionEventIntent;
import com.bank.esps.domain.cdm.event.QuantityChangeDirection;
import com.bank.esps.domain.cdm.event.QuantityChangeInstruction;
import com.bank.esps.domain.cdm.position.CounterpartyPositionState;

import java.math.BigDecimal;

/**
 * Qualifies a position business event from its primitives. Aligns with CDM
 * {@code [qualification BusinessEvent]} functions and
 * {@code PositionEventIntentEnum}.
 */
public final class QualifyPositionEvent {

    private QualifyPositionEvent() {
    }

    public static PositionEventIntent qualify(CounterpartyPositionState before,
                                              QuantityChangeInstruction instruction) {
        if (instruction == null || instruction.getDirection() == null) {
            throw new IllegalArgumentException("QuantityChangeInstruction is required to qualify an event");
        }
        boolean noOpenPosition = before == null
                || before.getCounterpartyPosition() == null
                || before.getCounterpartyPosition().openLots().isEmpty()
                || before.isClosed();
        if (instruction.getDirection() == QuantityChangeDirection.INCREASE && noOpenPosition) {
            return PositionEventIntent.POSITION_CREATION;
        }
        return switch (instruction.getDirection()) {
            case INCREASE -> PositionEventIntent.INCREASE;
            case DECREASE -> PositionEventIntent.DECREASE;
            case REPLACE -> qualifyReplace(before, instruction);
        };
    }

    private static PositionEventIntent qualifyReplace(CounterpartyPositionState before,
                                                      QuantityChangeInstruction instruction) {
        BigDecimal afterQty = instruction.changeQuantity();
        if (afterQty.compareTo(BigDecimal.ZERO) == 0) {
            return PositionEventIntent.DECREASE;
        }
        BigDecimal beforeQty = before == null ? BigDecimal.ZERO : before.totalQuantity().abs();
        if (afterQty.compareTo(beforeQty) > 0) {
            return PositionEventIntent.INCREASE;
        }
        if (afterQty.compareTo(beforeQty) < 0) {
            return PositionEventIntent.DECREASE;
        }
        return PositionEventIntent.VALUATION;
    }
}
