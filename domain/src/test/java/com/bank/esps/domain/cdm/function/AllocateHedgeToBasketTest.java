package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.base.Account;
import com.bank.esps.domain.cdm.base.Book;
import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.event.PositionTrade;
import com.bank.esps.domain.cdm.product.BasketComponent;
import com.bank.esps.domain.cdm.product.FinancialContract;
import com.bank.esps.domain.cdm.product.FinancialProduct;
import com.bank.esps.domain.cdm.product.ProductType;
import com.bank.esps.domain.cdm.product.Underlier;
import com.bank.esps.domain.cdm.product.UnderlierType;
import com.bank.esps.domain.cdm.repository.InMemoryBasketActivityRepository;
import com.bank.esps.domain.enums.TaxLotMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllocateHedgeToBasketTest {

    @Test
    void allocatesEquitySwapHedgeAndPersistsState() {
        FinancialContract contract = contract(FinancialProduct.equitySwap("AAPL", "USD"));
        InMemoryBasketActivityRepository repository = new InMemoryBasketActivityRepository();

        BasketActivity after = SaveBasketActivity.allocateAndSave(repository, contract, trade("H-1", "1000", "50"))
                .get(0);

        assertThat(after.getProduct().getProductType()).isEqualTo(ProductType.SWAP);
        assertThat(after.getProduct().underlierType()).isEqualTo(UnderlierType.EQUITY);
        assertThat(after.getDetails()).hasSize(1);
        assertThat(after.getOpenLots()).hasSize(1);
        assertThat(after.getSettlements()).hasSize(1);
        assertThat(after.getClosingLots()).isEmpty();
        assertThat(after.totalQuantity()).isEqualByComparingTo("1000");
        assertThat(repository.findByActivityId(after.getActivityId())).isPresent();
        assertThat(repository.findByActivityId(after.getActivityId()).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    void supportsCfdAndIndexAndBasketUnderliers() {
        assertThat(FinancialProduct.equityCfd("AAPL", "USD").getProductType()).isEqualTo(ProductType.CFD);
        assertThat(FinancialProduct.indexSwap("SPX", "USD").underlierType()).isEqualTo(UnderlierType.INDEX);
        FinancialProduct basket = FinancialProduct.basketSwap("TECH", "USD", List.of(
                BasketComponent.builder().underlier(Underlier.equity("AAPL", "USD")).weight(new BigDecimal("0.6")).build(),
                BasketComponent.builder().underlier(Underlier.equity("MSFT", "USD")).weight(new BigDecimal("0.4")).build()));
        assertThat(basket.underlierType()).isEqualTo(UnderlierType.BASKET);
        assertThat(basket.getUnderlier().getConstituents()).hasSize(2);

        BasketActivity cfd = AllocateHedgeToBasket.allocate(
                null, contract(FinancialProduct.equityCfd("AAPL", "USD")), trade("H-CFD", "10", "100"));
        assertThat(cfd.getProduct().getProductQualifier()).isEqualTo("EquityCFD");
        assertThat(cfd.getOpenLots()).hasSize(1);
    }

    @Test
    void decreaseWritesClosingLotsAndKeepsDetails() {
        FinancialContract contract = contract(FinancialProduct.equitySwap("AAPL", "USD"));
        InMemoryBasketActivityRepository repository = new InMemoryBasketActivityRepository();
        SaveBasketActivity.allocateAndSave(repository, contract, trade("H-1", "1000", "50"));

        BasketActivity after = SaveBasketActivity.allocateAndSave(repository, contract, trade("H-2", "-300", "60"))
                .get(0);

        assertThat(after.getDetails()).hasSize(2);
        assertThat(after.getOpenLots()).hasSize(1);
        assertThat(after.getOpenLots().get(0).remaining()).isEqualByComparingTo("700");
        assertThat(after.getClosingLots()).hasSize(1);
        assertThat(after.getClosingLots().get(0).getRealizedPnL()).isEqualByComparingTo("3000");
        assertThat(after.getSettlements()).hasSize(2);
        assertThat(after.getVersion()).isEqualTo(2);
    }

    @Test
    void fullCloseThenReopenUsesNewUpi() {
        FinancialContract contract = contract(FinancialProduct.equitySwap("AAPL", "USD"));
        InMemoryBasketActivityRepository repository = new InMemoryBasketActivityRepository();
        SaveBasketActivity.allocateAndSave(repository, contract, trade("H-1", "1000", "50"));
        BasketActivity closed = SaveBasketActivity.allocateAndSave(repository, contract, trade("H-2", "-1000", "65"))
                .get(0);
        assertThat(closed.isClosed()).isTrue();

        BasketActivity reopened = SaveBasketActivity.allocateAndSave(repository, contract, trade("H-3", "200", "70"))
                .get(0);
        assertThat(reopened.isClosed()).isFalse();
        assertThat(reopened.getUpi()).isEqualTo("H-3");
        assertThat(reopened.getOpenLots()).hasSize(1);
        assertThat(reopened.getDetails()).hasSize(3);
    }

    @Test
    void longToShortFlipSavesTwoActivityStates() {
        FinancialContract contract = contract(FinancialProduct.equitySwap("AAPL", "USD"));
        InMemoryBasketActivityRepository repository = new InMemoryBasketActivityRepository();
        SaveBasketActivity.allocateAndSave(repository, contract, trade("H-1", "1000", "50"));

        List<BasketActivity> result = SaveBasketActivity.allocateAndSave(
                repository, contract, trade("H-2", "-1500", "55"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isClosed()).isTrue();
        assertThat(result.get(0).getDirection()).isEqualTo(PositionDirection.LONG);
        assertThat(result.get(1).getDirection()).isEqualTo(PositionDirection.SHORT);
        assertThat(result.get(1).totalQuantity()).isEqualByComparingTo("-500");
        assertThat(repository.size()).isEqualTo(2);
    }

    private FinancialContract contract(FinancialProduct product) {
        return FinancialContract.builder()
                .contractId("C-EQ-1")
                .product(product)
                .account(Account.of("ACC1"))
                .book(Book.of("EQ-BOOK"))
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
    }

    private PositionTrade trade(String tradeId, String quantity, String price) {
        return PositionTrade.builder()
                .tradeId(tradeId)
                .accountId("ACC1")
                .bookId("EQ-BOOK")
                .instrumentId("AAPL")
                .currency("USD")
                .contractId("C-EQ-1")
                .quantity(new BigDecimal(quantity))
                .price(new BigDecimal(price))
                .tradeDate(LocalDate.of(2026, 1, 5))
                .settlementDate(LocalDate.of(2026, 1, 7))
                .build();
    }
}
