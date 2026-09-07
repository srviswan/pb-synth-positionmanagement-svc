package com.bank.esps.domain.cdm.position;

import com.bank.esps.domain.cdm.base.Identifier;
import com.bank.esps.domain.cdm.base.IdentifierType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Open lot within a position. Aligns with CDM {@code TradeLot} and carries
 * ESPS tax-lot economics (cost basis, remaining quantity, realized P&amp;L).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeLot {
    private Identifier lotIdentifier;
    @Builder.Default
    private List<PriceQuantity> priceQuantity = new ArrayList<>();
    private BigDecimal originalQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal costBasis;
    private BigDecimal currentRefPrice;
    private LocalDate tradeDate;
    private LocalDate settlementDate;
    private BigDecimal settledQuantity;
    private int acquisitionSequence;

    public static TradeLot open(BigDecimal quantity, BigDecimal price, String currency,
                                LocalDate tradeDate, LocalDate settlementDate) {
        LocalDate settlement = settlementDate != null ? settlementDate : tradeDate;
        return TradeLot.builder()
                .lotIdentifier(Identifier.of(IdentifierType.LOT, UUID.randomUUID().toString()))
                .priceQuantity(List.of(PriceQuantity.of(quantity, price, currency, tradeDate, settlement)))
                .originalQuantity(quantity)
                .remainingQuantity(quantity)
                .costBasis(price)
                .currentRefPrice(price)
                .tradeDate(tradeDate)
                .settlementDate(settlement)
                .settledQuantity(quantity)
                .build();
    }

    public String lotId() {
        return lotIdentifier != null ? lotIdentifier.firstValue() : null;
    }

    public BigDecimal remaining() {
        return remainingQuantity != null ? remainingQuantity : BigDecimal.ZERO;
    }

    public boolean isOpen() {
        return remaining().compareTo(BigDecimal.ZERO) != 0;
    }

    public BigDecimal remainingNotional() {
        BigDecimal price = currentRefPrice != null ? currentRefPrice : BigDecimal.ZERO;
        return remaining().multiply(price);
    }

    /**
     * Realized P&amp;L when reducing this lot. Long: (close - cost) * qty.
     * Short: (cost - close) * qty.
     */
    public BigDecimal calculateRealizedPnL(BigDecimal closePrice, BigDecimal closedQty, boolean shortPosition) {
        if (closePrice == null || closedQty == null || costBasis == null) {
            return BigDecimal.ZERO;
        }
        if (shortPosition) {
            return costBasis.subtract(closePrice).multiply(closedQty.abs());
        }
        return closePrice.subtract(costBasis).multiply(closedQty.abs());
    }

    public LotReduction reduce(BigDecimal qtyToReduce, BigDecimal closePrice, boolean shortPosition) {
        BigDecimal available = remaining().abs();
        BigDecimal reduced = qtyToReduce.abs().min(available);
        BigDecimal signedRemaining = remaining();
        BigDecimal signedReduced = signedRemaining.signum() < 0 ? reduced.negate() : reduced;
        BigDecimal newRemaining = signedRemaining.subtract(signedReduced);
        BigDecimal pnl = calculateRealizedPnL(closePrice, reduced, shortPosition);
        this.remainingQuantity = newRemaining;
        return new LotReduction(lotId(), reduced, newRemaining, pnl);
    }

    public record LotReduction(String lotId, BigDecimal reducedQuantity, BigDecimal remainingQuantity,
                               BigDecimal realizedPnL) {
    }
}
