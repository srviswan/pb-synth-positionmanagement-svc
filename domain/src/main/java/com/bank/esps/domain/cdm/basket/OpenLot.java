package com.bank.esps.domain.cdm.basket;

import com.bank.esps.domain.cdm.position.TradeLot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Current open tax lot on a basket. Maps to the firm's
 * {@code DOBasketActivityOpenLotTable}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenLot {
    private String lotId;
    private String sourceDetailId;
    private String sourceTradeId;
    private BigDecimal originalQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal costBasis;
    private BigDecimal currentRefPrice;
    private LocalDate tradeDate;
    private LocalDate settlementDate;
    private BigDecimal settledQuantity;
    private int acquisitionSequence;

    public static OpenLot from(TradeLot lot, String sourceDetailId, String sourceTradeId) {
        return OpenLot.builder()
                .lotId(lot.lotId())
                .sourceDetailId(sourceDetailId)
                .sourceTradeId(sourceTradeId)
                .originalQuantity(lot.getOriginalQuantity())
                .remainingQuantity(lot.getRemainingQuantity())
                .costBasis(lot.getCostBasis())
                .currentRefPrice(lot.getCurrentRefPrice())
                .tradeDate(lot.getTradeDate())
                .settlementDate(lot.getSettlementDate())
                .settledQuantity(lot.getSettledQuantity())
                .acquisitionSequence(lot.getAcquisitionSequence())
                .build();
    }

    public TradeLot toTradeLot() {
        return TradeLot.builder()
                .lotIdentifier(com.bank.esps.domain.cdm.base.Identifier.of(
                        com.bank.esps.domain.cdm.base.IdentifierType.LOT, lotId))
                .originalQuantity(originalQuantity)
                .remainingQuantity(remainingQuantity)
                .costBasis(costBasis)
                .currentRefPrice(currentRefPrice)
                .tradeDate(tradeDate)
                .settlementDate(settlementDate)
                .settledQuantity(settledQuantity)
                .acquisitionSequence(acquisitionSequence)
                .build();
    }

    public BigDecimal remaining() {
        return remainingQuantity != null ? remainingQuantity : BigDecimal.ZERO;
    }
}
