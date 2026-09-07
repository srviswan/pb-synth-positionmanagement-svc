package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.event.CounterpartyPositionBusinessEvent;
import com.bank.esps.domain.cdm.event.PositionEventIntent;
import com.bank.esps.domain.cdm.event.QuantityChangeDirection;
import com.bank.esps.domain.cdm.event.QuantityChangeInstruction;
import com.bank.esps.domain.cdm.position.CounterpartyPositionState;
import com.bank.esps.domain.cdm.position.PositionStatus;
import com.bank.esps.domain.cdm.position.PriceQuantity;
import com.bank.esps.domain.cdm.position.TradeLot;
import com.bank.esps.domain.enums.TaxLotMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateQuantityChangeTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 1, 5);
    private static final LocalDate SETTLEMENT_DATE = LocalDate.of(2026, 1, 7);

    @Test
    void createsPositionFromFirstIncrease() {
        CounterpartyPositionBusinessEvent event = increase(null, "T-1", "1000", "50.00");

        assertThat(event.getIntent()).isEqualTo(PositionEventIntent.POSITION_CREATION);
        assertThat(event.primaryAfter().totalQuantity()).isEqualByComparingTo("1000");
        assertThat(event.primaryAfter().getState().getPositionStatus()).isEqualTo(PositionStatus.FORMED);
        assertThat(event.primaryAfter().getCounterpartyPosition().upi()).isEqualTo("T-1");
        assertThat(event.primaryAfter().getCounterpartyPosition().openLots()).hasSize(1);
    }

    @Test
    void increaseAddsANewLot() {
        CounterpartyPositionState afterFirst = increase(null, "T-1", "1000", "50.00", TRADE_DATE).primaryAfter();
        CounterpartyPositionBusinessEvent event = increase(afterFirst, "T-2", "500", "55.00", TRADE_DATE.plusDays(1));

        assertThat(event.getIntent()).isEqualTo(PositionEventIntent.INCREASE);
        assertThat(event.primaryAfter().totalQuantity()).isEqualByComparingTo("1500");
        assertThat(event.primaryAfter().getCounterpartyPosition().openLots()).hasSize(2);
        assertThat(event.primaryAfter().getCounterpartyPosition().weightedAveragePrice())
                .isEqualByComparingTo("51.6667");
    }

    @Test
    void decreaseUsesFifoAndRecordsRealizedPnL() {
        CounterpartyPositionState state = increase(null, "T-1", "1000", "50.00", TRADE_DATE).primaryAfter();
        state = increase(state, "T-2", "500", "55.00", TRADE_DATE.plusDays(1)).primaryAfter();

        CounterpartyPositionBusinessEvent event = decrease(state, "T-3", "300", "60.00", TaxLotMethod.FIFO);

        assertThat(event.getIntent()).isEqualTo(PositionEventIntent.DECREASE);
        assertThat(event.primaryAfter().totalQuantity()).isEqualByComparingTo("1200");
        assertThat(event.primaryAfter().getRealizedPnL()).isEqualByComparingTo("3000");
        TradeLot oldest = event.primaryAfter().getCounterpartyPosition().openLots().get(0);
        assertThat(oldest.getCostBasis()).isEqualByComparingTo("50.00");
        assertThat(oldest.remaining()).isEqualByComparingTo("700");
    }

    @Test
    void decreaseUsesHifoAgainstHighestCostLot() {
        CounterpartyPositionState state = increase(null, "T-1", "1000", "50.00", TRADE_DATE).primaryAfter();
        state = increase(state, "T-2", "500", "55.00", TRADE_DATE.plusDays(1)).primaryAfter();

        CounterpartyPositionBusinessEvent event = decrease(state, "T-3", "200", "60.00", TaxLotMethod.HIFO);

        TradeLot remainingHigh = event.primaryAfter().getCounterpartyPosition().openLots().stream()
                .filter(lot -> lot.getCostBasis().compareTo(new BigDecimal("55.00")) == 0)
                .findFirst()
                .orElseThrow();
        assertThat(remainingHigh.remaining()).isEqualByComparingTo("300");
        assertThat(event.primaryAfter().getRealizedPnL()).isEqualByComparingTo("1000");
    }

    @Test
    void fullDecreaseClosesThePosition() {
        CounterpartyPositionState state = increase(null, "T-1", "1000", "50.00").primaryAfter();
        CounterpartyPositionBusinessEvent event = decrease(state, "T-2", "1000", "65.00", TaxLotMethod.FIFO);

        assertThat(event.primaryAfter().isClosed()).isTrue();
        assertThat(event.primaryAfter().getState().getClosedState().getReason().name()).isEqualTo("TERMINATED");
        assertThat(event.primaryAfter().totalQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(event.primaryAfter().getCounterpartyPosition().upi()).isEqualTo("T-1");
        assertThat(event.primaryAfter().getRealizedPnL()).isEqualByComparingTo("15000");
    }

    @Test
    void newIncreaseOnClosedPositionReopensWithNewUpi() {
        CounterpartyPositionState closed = decrease(
                increase(null, "T-1", "1000", "50.00").primaryAfter(),
                "T-2", "1000", "65.00", TaxLotMethod.FIFO).primaryAfter();

        CounterpartyPositionBusinessEvent event = increase(closed, "T-3", "2000", "70.00");

        assertThat(event.getIntent()).isEqualTo(PositionEventIntent.POSITION_CREATION);
        assertThat(event.primaryAfter().isClosed()).isFalse();
        assertThat(event.primaryAfter().getCounterpartyPosition().upi()).isEqualTo("T-3");
        assertThat(event.primaryAfter().totalQuantity()).isEqualByComparingTo("2000");
        assertThat(event.primaryAfter().getCounterpartyPosition().openLots()).hasSize(1);
    }

    @Test
    void sameDayFifoUsesAcquisitionSequence() {
        CounterpartyPositionState state = increase(null, "T-1", "100", "10.00").primaryAfter();
        state = increase(state, "T-2", "100", "20.00").primaryAfter();

        CounterpartyPositionBusinessEvent event = decrease(state, "T-3", "100", "30.00", TaxLotMethod.FIFO);

        assertThat(event.primaryAfter().getRealizedPnL()).isEqualByComparingTo("2000");
        assertThat(event.primaryAfter().getCounterpartyPosition().openLots()).hasSize(1);
        assertThat(event.primaryAfter().getCounterpartyPosition().openLots().get(0).getCostBasis())
                .isEqualByComparingTo("20.00");
    }

    @Test
    void recordsHybridSettlementOnTheSchedule() {
        CounterpartyPositionBusinessEvent event = increase(null, "T-1", "1000", "50.00");
        PriceQuantity schedule = event.primaryAfter().getCounterpartyPosition().getPriceQuantitySchedule().get(0);

        assertThat(schedule.getTradeDate()).isEqualTo(TRADE_DATE);
        assertThat(schedule.getSettlementDate()).isEqualTo(SETTLEMENT_DATE);
        assertThat(schedule.getEffectiveDate()).isEqualTo(SETTLEMENT_DATE);
        assertThat(schedule.quantityAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void rejectsNonPositiveChangeQuantity() {
        QuantityChangeInstruction instruction = QuantityChangeInstruction.builder()
                .direction(QuantityChangeDirection.INCREASE)
                .change(List.of(PriceQuantity.of(BigDecimal.ZERO, new BigDecimal("50"), "USD", TRADE_DATE, SETTLEMENT_DATE)))
                .build();

        assertThatThrownBy(() -> CreateQuantityChange.apply(
                null, instruction, CreateQuantityChange.QuantityChangeContext.of("T-1", "ACC1", "AAPL", "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    private CounterpartyPositionBusinessEvent increase(CounterpartyPositionState before, String tradeId,
                                                       String quantity, String price) {
        return increase(before, tradeId, quantity, price, TRADE_DATE);
    }

    private CounterpartyPositionBusinessEvent increase(CounterpartyPositionState before, String tradeId,
                                                       String quantity, String price, LocalDate tradeDate) {
        QuantityChangeInstruction instruction = QuantityChangeInstruction.builder()
                .direction(QuantityChangeDirection.INCREASE)
                .change(List.of(PriceQuantity.of(new BigDecimal(quantity), new BigDecimal(price),
                        "USD", tradeDate, tradeDate.plusDays(2))))
                .build();
        return CreateQuantityChange.apply(before, instruction,
                new CreateQuantityChange.QuantityChangeContext(
                        tradeId, "ACC1", "EQ-BOOK", "ACC1", "AAPL", "USD", "C-1",
                        null, TaxLotMethod.FIFO, null));
    }

    private CounterpartyPositionBusinessEvent decrease(CounterpartyPositionState before, String tradeId,
                                                       String quantity, String price, TaxLotMethod method) {
        QuantityChangeInstruction instruction = QuantityChangeInstruction.builder()
                .direction(QuantityChangeDirection.DECREASE)
                .change(List.of(PriceQuantity.of(new BigDecimal(quantity), new BigDecimal(price),
                        "USD", TRADE_DATE, SETTLEMENT_DATE)))
                .taxLotMethod(method)
                .build();
        return CreateQuantityChange.apply(before, instruction,
                CreateQuantityChange.QuantityChangeContext.of(tradeId, "ACC1", "AAPL", "USD"));
    }
}
