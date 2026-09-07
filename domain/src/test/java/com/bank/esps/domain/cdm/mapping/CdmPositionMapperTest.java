package com.bank.esps.domain.cdm.mapping;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.event.PositionTrade;
import com.bank.esps.domain.cdm.function.ApplyPositionTrade;
import com.bank.esps.domain.cdm.position.CounterpartyPositionState;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.PositionState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CdmPositionMapperTest {

    @Test
    void mapsTradeEventToPositionTradeAndBack() {
        TradeEvent incoming = TradeEvent.builder()
                .tradeId("T-9")
                .account("ACC1")
                .book("EQ-BOOK")
                .instrument("AAPL")
                .currency("USD")
                .contractId("C-1")
                .quantity(new BigDecimal("250"))
                .price(new BigDecimal("12.5"))
                .tradeDate(LocalDate.of(2026, 2, 1))
                .effectiveDate(LocalDate.of(2026, 2, 3))
                .settlementDate(LocalDate.of(2026, 2, 3))
                .build();

        PositionTrade trade = CdmPositionMapper.toPositionTrade(incoming);
        TradeEvent roundTrip = CdmPositionMapper.toTradeEvent(trade);

        assertThat(trade.getAccountId()).isEqualTo("ACC1");
        assertThat(roundTrip.getTradeId()).isEqualTo("T-9");
        assertThat(roundTrip.getSettlementDate()).isEqualTo(LocalDate.of(2026, 2, 3));
        assertThat(roundTrip.getQuantity()).isEqualByComparingTo("250");
    }

    @Test
    void mapsCdmStateToLegacyPositionState() {
        PositionTrade trade = PositionTrade.builder()
                .tradeId("T-1")
                .accountId("ACC1")
                .bookId("EQ-BOOK")
                .instrumentId("AAPL")
                .currency("USD")
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("10"))
                .tradeDate(LocalDate.of(2026, 1, 5))
                .settlementDate(LocalDate.of(2026, 1, 7))
                .build();
        CounterpartyPositionState cdm = ApplyPositionTrade.apply(null, trade).get(0).primaryAfter();

        PositionState legacy = CdmPositionMapper.toPositionState(cdm);
        CounterpartyPositionState back = CdmPositionMapper.toCdmState(legacy, PositionDirection.LONG);

        assertThat(legacy.getAccount()).isEqualTo("ACC1");
        assertThat(legacy.getOpenLots()).hasSize(1);
        assertThat(legacy.getTotalQty()).isEqualByComparingTo("100");
        assertThat(legacy.getPriceQuantitySchedule().getSchedule()).isNotEmpty();
        assertThat(back.totalQuantity()).isEqualByComparingTo("100");
        assertThat(back.getCounterpartyPosition().getDirection()).isEqualTo(PositionDirection.LONG);
    }
}
