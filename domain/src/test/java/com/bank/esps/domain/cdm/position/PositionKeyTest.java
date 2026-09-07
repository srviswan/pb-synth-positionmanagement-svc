package com.bank.esps.domain.cdm.position;

import com.bank.esps.domain.cdm.base.PositionDirection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PositionKeyTest {

    @Test
    void contractSecurityDirectionIsThePositionGrain() {
        PositionKey first = PositionKey.of("c-eq-1", "aapl", PositionDirection.LONG);
        PositionKey second = PositionKey.of(" C-EQ-1 ", "AAPL", PositionDirection.LONG);
        PositionKey shortKey = PositionKey.of("C-EQ-1", "AAPL", PositionDirection.SHORT);
        PositionKey otherSecurity = PositionKey.of("C-EQ-1", "MSFT", PositionDirection.LONG);
        PositionKey otherContract = PositionKey.of("C-EQ-2", "AAPL", PositionDirection.LONG);

        assertThat(first.getValue()).isEqualTo(second.getValue());
        assertThat(first.getValue()).hasSize(16);
        assertThat(first.getContractId()).isEqualTo("C-EQ-1");
        assertThat(first.getSecurityId()).isEqualTo("AAPL");
        assertThat(shortKey.getValue()).isNotEqualTo(first.getValue());
        assertThat(first.opposite().getValue()).isEqualTo(shortKey.getValue());
        assertThat(otherSecurity.getValue()).isNotEqualTo(first.getValue());
        assertThat(otherContract.getValue()).isNotEqualTo(first.getValue());
    }

    @Test
    void accountInstrumentKeyRemainsForEventStorePath() {
        PositionKey first = PositionKey.of("acc1", "aapl", "usd", PositionDirection.LONG);
        PositionKey second = PositionKey.of(" ACC1 ", "AAPL", "USD", PositionDirection.LONG);
        PositionKey shortKey = PositionKey.of("ACC1", "AAPL", "USD", PositionDirection.SHORT);
        PositionKey grain = PositionKey.of("ACC1", "AAPL", PositionDirection.LONG);

        assertThat(first.getValue()).isEqualTo(second.getValue());
        assertThat(first.getValue()).hasSize(16);
        assertThat(shortKey.getValue()).isNotEqualTo(first.getValue());
        assertThat(first.opposite().getValue()).isEqualTo(shortKey.getValue());
        assertThat(grain.getValue()).isNotEqualTo(first.getValue());
    }
}
