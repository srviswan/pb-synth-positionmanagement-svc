package com.bank.esps.infrastructure.persistence.mapping;

import com.bank.esps.domain.cdm.base.Account;
import com.bank.esps.domain.cdm.base.Book;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.event.PositionTrade;
import com.bank.esps.domain.cdm.function.AllocateHedgeToBasket;
import com.bank.esps.domain.cdm.product.FinancialContract;
import com.bank.esps.domain.cdm.product.FinancialProduct;
import com.bank.esps.domain.cdm.product.ProductType;
import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.infrastructure.persistence.entity.BasketActivityEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BasketActivityPersistenceMapperTest {

    @Test
    void roundTripsProductJsonAndChildRows() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        BasketActivityPersistenceMapper mapper = new BasketActivityPersistenceMapper(objectMapper);
        FinancialContract contract = FinancialContract.builder()
                .contractId("C-1")
                .product(FinancialProduct.equityCfd("AAPL", "USD"))
                .account(Account.of("ACC1"))
                .book(Book.of("EQ-BOOK"))
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        BasketActivity activity = AllocateHedgeToBasket.allocate(null, contract, PositionTrade.builder()
                .tradeId("H-1")
                .accountId("ACC1")
                .instrumentId("AAPL")
                .currency("USD")
                .quantity(new BigDecimal("25"))
                .price(new BigDecimal("11"))
                .tradeDate(LocalDate.of(2026, 3, 1))
                .settlementDate(LocalDate.of(2026, 3, 3))
                .build());

        BasketActivityEntity header = mapper.toEntity(activity);
        BasketActivity restored = mapper.toDomain(
                header,
                List.of(mapper.toDetailEntity(activity.getActivityId(), activity.getDetails().get(0))),
                List.of(mapper.toOpenLotEntity(activity.getActivityId(), activity.getOpenLots().get(0))),
                List.of(),
                List.of(mapper.toSettlementEntity(activity.getActivityId(), activity.getSettlements().get(0))));

        assertThat(header.getProductType()).isEqualTo(ProductType.CFD.name());
        assertThat(header.getUnderlierId()).isEqualTo("AAPL");
        assertThat(header.getSecurityId()).isEqualTo("AAPL");
        assertThat(restored.getProduct().getProductType()).isEqualTo(ProductType.CFD);
        assertThat(restored.getSecurityId()).isEqualTo("AAPL");
        assertThat(restored.getDetails()).hasSize(1);
        assertThat(restored.getOpenLots()).hasSize(1);
        assertThat(restored.getSettlements()).hasSize(1);
        assertThat(restored.totalQuantity()).isEqualByComparingTo("25");
    }
}
