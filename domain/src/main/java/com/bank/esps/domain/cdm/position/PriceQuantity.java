package com.bank.esps.domain.cdm.position;

import com.bank.esps.domain.cdm.base.Price;
import com.bank.esps.domain.cdm.base.Quantity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Price and quantity that become effective on a date. Aligns with CDM
 * {@code PriceQuantity}.
 *
 * <p>ESPS hybrid settlement: {@code tradeDate} is used for position tracking,
 * {@code effectiveDate} / {@code settlementDate} for interest accrual, and
 * {@code settledQuantity} may differ from {@code quantity} on partial settlement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceQuantity {
    private Quantity quantity;
    private Quantity settledQuantity;
    private Price price;
    private LocalDate tradeDate;
    private LocalDate settlementDate;
    private LocalDate effectiveDate;

    public static PriceQuantity of(BigDecimal quantity, BigDecimal price, String currency,
                                   LocalDate tradeDate, LocalDate settlementDate) {
        LocalDate settlement = settlementDate != null ? settlementDate : tradeDate;
        return PriceQuantity.builder()
                .quantity(Quantity.ofShares(quantity))
                .settledQuantity(Quantity.ofShares(quantity))
                .price(Price.assetPrice(price, currency))
                .tradeDate(tradeDate)
                .settlementDate(settlement)
                .effectiveDate(settlement)
                .build();
    }

    public BigDecimal quantityAmount() {
        return quantity != null ? quantity.amount() : BigDecimal.ZERO;
    }

    public BigDecimal settledQuantityAmount() {
        if (settledQuantity != null) {
            return settledQuantity.amount();
        }
        return quantityAmount();
    }

    public BigDecimal priceAmount() {
        return price != null ? price.amount() : BigDecimal.ZERO;
    }

    public BigDecimal notional() {
        return quantityAmount().multiply(priceAmount());
    }

    public BigDecimal settledNotional() {
        return settledQuantityAmount().multiply(priceAmount());
    }

    public LocalDate accrualStartDate() {
        if (effectiveDate != null) {
            return effectiveDate;
        }
        if (settlementDate != null) {
            return settlementDate;
        }
        return tradeDate;
    }
}
