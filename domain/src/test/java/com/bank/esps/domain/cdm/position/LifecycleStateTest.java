package com.bank.esps.domain.cdm.position;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleStateTest {

    @Test
    void closedStateMustExistWhenStatusIsClosed() {
        LifecycleState invalid = LifecycleState.builder()
                .positionStatus(PositionStatus.CLOSED)
                .build();

        assertThatThrownBy(invalid::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ClosedStateExists");
    }

    @Test
    void closedFactorySatisfiesCdmCondition() {
        LifecycleState closed = LifecycleState.closed(ClosedState.terminated(LocalDate.of(2026, 1, 7)));

        closed.validate();
        assertThat(closed.isClosed()).isTrue();
        assertThat(closed.getClosedState().getReason()).isEqualTo(ClosedStateReason.TERMINATED);
    }
}
