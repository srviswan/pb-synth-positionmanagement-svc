package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.event.CounterpartyPositionBusinessEvent;
import com.bank.esps.domain.cdm.event.PositionEventIntent;
import com.bank.esps.domain.cdm.event.PositionTrade;
import com.bank.esps.domain.cdm.position.CounterpartyPositionState;
import com.bank.esps.domain.cdm.position.PositionKey;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplyPositionTradeTest {

    @Test
    void appliesSignedIncreaseAndDecrease() {
        PositionTrade open = trade("T-1", "1000", "50");
        CounterpartyPositionState longState = ApplyPositionTrade.apply(null, open).get(0).primaryAfter();

        PositionTrade reduce = trade("T-2", "-400", "60");
        CounterpartyPositionBusinessEvent decrease = ApplyPositionTrade.apply(longState, reduce).get(0);

        assertThat(decrease.getIntent()).isEqualTo(PositionEventIntent.DECREASE);
        assertThat(decrease.primaryAfter().totalQuantity()).isEqualByComparingTo("600");
        assertThat(decrease.primaryAfter().getRealizedPnL()).isEqualByComparingTo("4000");
    }

    @Test
    void flipsLongToShortIntoTwoBusinessEvents() {
        CounterpartyPositionState longState = ApplyPositionTrade.apply(null, trade("T-1", "1000", "50"))
                .get(0).primaryAfter();

        List<CounterpartyPositionBusinessEvent> events =
                ApplyPositionTrade.apply(longState, trade("T-2", "-1500", "55"));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventQualifier()).isEqualTo("DIRECTION_FLIP_CLOSE");
        assertThat(events.get(0).primaryAfter().isClosed()).isTrue();
        assertThat(events.get(0).primaryAfter().getCounterpartyPosition().getDirection())
                .isEqualTo(PositionDirection.LONG);

        assertThat(events.get(1).getIntent()).isEqualTo(PositionEventIntent.POSITION_CREATION);
        assertThat(events.get(1).primaryAfter().getCounterpartyPosition().getDirection())
                .isEqualTo(PositionDirection.SHORT);
        assertThat(events.get(1).primaryAfter().totalQuantity()).isEqualByComparingTo("-500");
        assertThat(events.get(1).primaryAfter().positionKey())
                .isEqualTo(PositionKey.of("ACC1", "AAPL", "USD", PositionDirection.SHORT).getValue());
        assertThat(events.get(1).primaryAfter().positionKey())
                .isNotEqualTo(events.get(0).primaryAfter().positionKey());
    }

    @Test
    void flipsShortToLongIntoTwoBusinessEvents() {
        CounterpartyPositionState shortState = ApplyPositionTrade.apply(null, trade("T-1", "-800", "40"))
                .get(0).primaryAfter();
        assertThat(shortState.getCounterpartyPosition().getDirection()).isEqualTo(PositionDirection.SHORT);

        List<CounterpartyPositionBusinessEvent> events =
                ApplyPositionTrade.apply(shortState, trade("T-2", "1200", "42"));

        assertThat(events.get(0).primaryAfter().isClosed()).isTrue();
        assertThat(events.get(1).primaryAfter().getCounterpartyPosition().getDirection())
                .isEqualTo(PositionDirection.LONG);
        assertThat(events.get(1).primaryAfter().totalQuantity()).isEqualByComparingTo("400");
    }

    @Test
    void rejectsZeroQuantity() {
        assertThatThrownBy(() -> ApplyPositionTrade.apply(null, trade("T-0", "0", "10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-zero");
    }

    private PositionTrade trade(String tradeId, String quantity, String price) {
        return PositionTrade.builder()
                .tradeId(tradeId)
                .accountId("ACC1")
                .bookId("EQ-BOOK")
                .instrumentId("AAPL")
                .currency("USD")
                .quantity(new BigDecimal(quantity))
                .price(new BigDecimal(price))
                .tradeDate(LocalDate.of(2026, 1, 5))
                .settlementDate(LocalDate.of(2026, 1, 7))
                .build();
    }
}
