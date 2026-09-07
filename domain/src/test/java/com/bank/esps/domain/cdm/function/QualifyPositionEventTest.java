package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.event.PositionEventIntent;
import com.bank.esps.domain.cdm.event.QuantityChangeDirection;
import com.bank.esps.domain.cdm.event.QuantityChangeInstruction;
import com.bank.esps.domain.cdm.position.ClosedState;
import com.bank.esps.domain.cdm.position.CounterpartyPosition;
import com.bank.esps.domain.cdm.position.CounterpartyPositionState;
import com.bank.esps.domain.cdm.position.LifecycleState;
import com.bank.esps.domain.cdm.position.PriceQuantity;
import com.bank.esps.domain.cdm.position.TradeLot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualifyPositionEventTest {

    @Test
    void qualifiesCreationIncreaseAndDecrease() {
        QuantityChangeInstruction increase = instruction(QuantityChangeDirection.INCREASE, "100");
        assertThat(QualifyPositionEvent.qualify(null, increase)).isEqualTo(PositionEventIntent.POSITION_CREATION);

        CounterpartyPositionState open = openState();
        assertThat(QualifyPositionEvent.qualify(open, increase)).isEqualTo(PositionEventIntent.INCREASE);
        assertThat(QualifyPositionEvent.qualify(open, instruction(QuantityChangeDirection.DECREASE, "40")))
                .isEqualTo(PositionEventIntent.DECREASE);
    }

    @Test
    void treatsIncreaseOnClosedPositionAsCreation() {
        CounterpartyPositionState closed = openState();
        closed.close(ClosedState.terminated(LocalDate.of(2026, 1, 7)));

        assertThat(QualifyPositionEvent.qualify(closed, instruction(QuantityChangeDirection.INCREASE, "10")))
                .isEqualTo(PositionEventIntent.POSITION_CREATION);
    }

    private QuantityChangeInstruction instruction(QuantityChangeDirection direction, String quantity) {
        return QuantityChangeInstruction.builder()
                .direction(direction)
                .change(List.of(PriceQuantity.of(new BigDecimal(quantity), new BigDecimal("10"),
                        "USD", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7))))
                .build();
    }

    private CounterpartyPositionState openState() {
        return CounterpartyPositionState.builder()
                .state(LifecycleState.formed())
                .counterpartyPosition(CounterpartyPosition.builder()
                        .tradeLot(new ArrayList<>(List.of(
                                TradeLot.open(new BigDecimal("100"), new BigDecimal("10"), "USD",
                                        LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7)))))
                        .build())
                .build();
    }
}
