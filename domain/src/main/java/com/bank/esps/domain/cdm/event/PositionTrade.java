package com.bank.esps.domain.cdm.event;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.domain.enums.TradeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Service-level trade input that maps onto a CDM quantity-change primitive.
 * Quantity is signed: positive increases a long / covers a short; negative
 * decreases a long / increases a short.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionTrade {
    private String tradeId;
    private String accountId;
    private String bookId;
    private String partyId;
    private String instrumentId;
    private String currency;
    private String contractId;
    private String positionKey;
    private BigDecimal quantity;
    private BigDecimal price;
    private LocalDate tradeDate;
    private LocalDate effectiveDate;
    private LocalDate settlementDate;
    private TradeType tradeType;
    @Builder.Default
    private TaxLotMethod taxLotMethod = TaxLotMethod.FIFO;

    public BigDecimal signedQuantity() {
        return quantity != null ? quantity : BigDecimal.ZERO;
    }

    public PositionDirection impliedDirection() {
        return signedQuantity().compareTo(BigDecimal.ZERO) < 0
                ? PositionDirection.SHORT
                : PositionDirection.LONG;
    }

    public QuantityChangeDirection changeDirection(PositionDirection positionDirection) {
        boolean increasing = positionDirection == PositionDirection.SHORT
                ? signedQuantity().compareTo(BigDecimal.ZERO) < 0
                : signedQuantity().compareTo(BigDecimal.ZERO) > 0;
        return increasing ? QuantityChangeDirection.INCREASE : QuantityChangeDirection.DECREASE;
    }
}
