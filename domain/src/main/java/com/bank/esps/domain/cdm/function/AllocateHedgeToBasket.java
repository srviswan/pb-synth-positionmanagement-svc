package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.basket.BasketActivityDetail;
import com.bank.esps.domain.cdm.basket.BasketSettlement;
import com.bank.esps.domain.cdm.basket.ClosingLot;
import com.bank.esps.domain.cdm.basket.OpenLot;
import com.bank.esps.domain.cdm.event.PositionTrade;
import com.bank.esps.domain.cdm.event.QuantityChangeDirection;
import com.bank.esps.domain.cdm.position.TradeLot;
import com.bank.esps.domain.cdm.product.FinancialContract;
import com.bank.esps.domain.cdm.product.Underlier;
import com.bank.esps.domain.enums.TaxLotMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Allocates a hedge trade onto a {@link BasketActivity} and mutates the
 * persisted state (details, open lots, closing lots, settlements).
 */
public final class AllocateHedgeToBasket {

    private AllocateHedgeToBasket() {
    }

    public static BasketActivity allocate(BasketActivity activity, FinancialContract contract, PositionTrade trade) {
        if (trade == null || trade.getTradeId() == null || trade.getTradeId().isBlank()) {
            throw new IllegalArgumentException("Hedge tradeId is required");
        }
        if (trade.signedQuantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Hedge quantity must be non-zero");
        }
        if (activity == null) {
            activity = BasketActivity.open(contract, trade.impliedDirection());
        }
        if (activity.getUpi() == null) {
            activity.setUpi(trade.getTradeId());
        }

        PositionDirection direction = activity.getDirection();
        QuantityChangeDirection changeDirection = trade.changeDirection(direction);
        if (activity.isClosed() && changeDirection == QuantityChangeDirection.INCREASE) {
            activity.reopen(trade.getTradeId());
        }

        BasketActivityDetail detail = detailFrom(activity, trade);
        activity.getDetails().add(detail);
        activity.getSettlements().add(settlementFrom(detail));

        if (changeDirection == QuantityChangeDirection.INCREASE) {
            addOpenLot(activity, detail, trade);
        } else {
            closeLots(activity, detail, trade);
        }

        activity.closeIfFlat(detail.getSettlementDate() != null ? detail.getSettlementDate() : detail.getTradeDate());
        activity.setVersion(activity.getVersion() + 1);
        activity.setUpdatedAt(OffsetDateTime.now());
        return activity;
    }

    private static void addOpenLot(BasketActivity activity, BasketActivityDetail detail, PositionTrade trade) {
        boolean shortPosition = activity.isShort();
        BigDecimal signed = shortPosition ? trade.signedQuantity().abs().negate() : trade.signedQuantity().abs();
        TradeLot lot = TradeLot.open(signed, trade.getPrice(), trade.getCurrency(),
                trade.getTradeDate(), trade.getSettlementDate());
        lot.setAcquisitionSequence(activity.getOpenLots().size());
        activity.getOpenLots().add(OpenLot.from(lot, detail.getDetailId(), trade.getTradeId()));
    }

    private static void closeLots(BasketActivity activity, BasketActivityDetail detail, PositionTrade trade) {
        List<OpenLot> beforeLots = new ArrayList<>(activity.getOpenLots());
        List<TradeLot> lots = new ArrayList<>();
        for (OpenLot openLot : beforeLots) {
            lots.add(openLot.toTradeLot());
        }
        TaxLotMethod method = trade.getTaxLotMethod() != null ? trade.getTaxLotMethod() : activity.getTaxLotMethod();
        List<TradeLot.LotReduction> reductions = LotAllocation.reduce(
                lots, trade.signedQuantity().abs(), method, trade.getPrice(), activity.isShort());

        List<OpenLot> remaining = new ArrayList<>();
        for (TradeLot lot : lots) {
            remaining.add(copyRemaining(beforeLots, lot));
        }
        activity.setOpenLots(remaining);

        for (TradeLot.LotReduction reduction : reductions) {
            OpenLot original = beforeLots.stream()
                    .filter(lot -> reduction.lotId().equals(lot.getLotId()))
                    .findFirst()
                    .orElse(null);
            activity.getClosingLots().add(ClosingLot.builder()
                    .closingLotId(UUID.randomUUID().toString())
                    .openedLotId(reduction.lotId())
                    .closingDetailId(detail.getDetailId())
                    .closingTradeId(trade.getTradeId())
                    .closedQuantity(reduction.reducedQuantity())
                    .closePrice(trade.getPrice())
                    .costBasis(original != null ? original.getCostBasis() : null)
                    .realizedPnL(reduction.realizedPnL())
                    .tradeDate(trade.getTradeDate())
                    .settlementDate(trade.getSettlementDate())
                    .build());
            activity.setRealizedPnL(activity.getRealizedPnL().add(reduction.realizedPnL()));
        }
    }

    private static OpenLot copyRemaining(List<OpenLot> beforeLots, TradeLot lot) {
        OpenLot previous = beforeLots.stream()
                .filter(open -> lot.lotId().equals(open.getLotId()))
                .findFirst()
                .orElse(null);
        OpenLot remaining = OpenLot.from(lot,
                previous != null ? previous.getSourceDetailId() : null,
                previous != null ? previous.getSourceTradeId() : null);
        remaining.setLotId(lot.lotId());
        return remaining;
    }

    private static BasketActivityDetail detailFrom(BasketActivity activity, PositionTrade trade) {
        Underlier underlier = activity.getProduct() != null ? activity.getProduct().getUnderlier() : null;
        LocalDate settlement = trade.getSettlementDate() != null ? trade.getSettlementDate() : trade.getTradeDate();
        return BasketActivityDetail.builder()
                .detailId(UUID.randomUUID().toString())
                .tradeId(trade.getTradeId())
                .underlier(underlier)
                .quantity(trade.getQuantity())
                .price(trade.getPrice())
                .currency(trade.getCurrency())
                .tradeDate(trade.getTradeDate())
                .effectiveDate(trade.getEffectiveDate() != null ? trade.getEffectiveDate() : settlement)
                .settlementDate(settlement)
                .allocationStatus("ALLOCATED")
                .recordedAt(OffsetDateTime.now())
                .build();
    }

    private static BasketSettlement settlementFrom(BasketActivityDetail detail) {
        return BasketSettlement.builder()
                .settlementId(UUID.randomUUID().toString())
                .detailId(detail.getDetailId())
                .tradeId(detail.getTradeId())
                .settlementDate(detail.getSettlementDate())
                .settledQuantity(detail.getQuantity() != null ? detail.getQuantity().abs() : BigDecimal.ZERO)
                .currency(detail.getCurrency())
                .status("SETTLED")
                .build();
    }
}
