package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.base.Account;
import com.bank.esps.domain.cdm.base.Book;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.event.DividendInstruction;
import com.bank.esps.domain.cdm.event.PositionTrade;
import com.bank.esps.domain.cdm.product.FinancialContract;
import com.bank.esps.domain.cdm.product.FinancialProduct;
import com.bank.esps.domain.cdm.repository.InMemoryBasketActivityRepository;
import com.bank.esps.domain.enums.TaxLotMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyDividendToBasketTest {

    @Test
    void opensAndClosesDividendLotsAgainstPositionOpenLots() {
        FinancialContract contract = FinancialContract.builder()
                .contractId("C-EQ-1")
                .product(FinancialProduct.equitySwap("AAPL", "USD"))
                .account(Account.of("ACC1"))
                .book(Book.of("EQ-BOOK"))
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        InMemoryBasketActivityRepository repository = new InMemoryBasketActivityRepository();
        BasketActivity position = SaveBasketActivity.allocateAndSave(repository, contract, PositionTrade.builder()
                .tradeId("H-1")
                .accountId("ACC1")
                .instrumentId("AAPL")
                .currency("USD")
                .contractId("C-EQ-1")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("50"))
                .tradeDate(LocalDate.of(2026, 1, 5))
                .settlementDate(LocalDate.of(2026, 1, 7))
                .build()).get(0);

        ApplyDividendToBasket.apply(position, DividendInstruction.builder()
                .dividendId("DIV-1")
                .exDate(LocalDate.of(2026, 2, 1))
                .payDate(LocalDate.of(2026, 2, 15))
                .rate(new BigDecimal("0.52"))
                .currency("USD")
                .action(DividendInstruction.Action.OPEN)
                .build());
        repository.save(position);

        assertThat(position.getDividendOpenLots()).hasSize(1);
        assertThat(position.getDividendOpenLots().get(0).getAmount()).isEqualByComparingTo("520.00");
        assertThat(position.getDividendOpenLots().get(0).getSourceOpenLotId())
                .isEqualTo(position.getOpenLots().get(0).getLotId());

        ApplyDividendToBasket.apply(position, DividendInstruction.builder()
                .dividendId("DIV-1")
                .payDate(LocalDate.of(2026, 2, 15))
                .action(DividendInstruction.Action.CLOSE)
                .build());
        repository.save(position);

        assertThat(position.getDividendOpenLots()).isEmpty();
        assertThat(position.getDividendClosingLots()).hasSize(1);
        assertThat(position.getDividendClosingLots().get(0).getClosedQuantity()).isEqualByComparingTo("1000");
        assertThat(position.getDividendClosingLots().get(0).getAmount()).isEqualByComparingTo("520.00");
        assertThat(repository.findByActivityId(position.getActivityId()).orElseThrow().getDividendClosingLots())
                .hasSize(1);
    }
}
