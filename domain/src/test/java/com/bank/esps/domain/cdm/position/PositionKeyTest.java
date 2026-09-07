package com.bank.esps.domain.cdm.position;

import com.bank.esps.domain.cdm.base.PositionDirection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PositionKeyTest {

    @Test
    void isDeterministicAndDirectionAware() {
        PositionKey first = PositionKey.of("acc1", "aapl", "usd", PositionDirection.LONG);
        PositionKey second = PositionKey.of(" ACC1 ", "AAPL", "USD", PositionDirection.LONG);
        PositionKey shortKey = PositionKey.of("ACC1", "AAPL", "USD", PositionDirection.SHORT);

        assertThat(first.getValue()).isEqualTo(second.getValue());
        assertThat(first.getValue()).hasSize(16);
        assertThat(shortKey.getValue()).isNotEqualTo(first.getValue());
        assertThat(first.opposite().getValue()).isEqualTo(shortKey.getValue());
    }
}
