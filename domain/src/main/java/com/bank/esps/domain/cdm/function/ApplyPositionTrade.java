package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.event.CounterpartyPositionBusinessEvent;
import com.bank.esps.domain.cdm.event.PositionEventIntent;
import com.bank.esps.domain.cdm.event.PositionTrade;
import com.bank.esps.domain.cdm.event.QuantityChangeDirection;
import com.bank.esps.domain.cdm.event.QuantityChangeInstruction;
import com.bank.esps.domain.cdm.position.ClosedState;
import com.bank.esps.domain.cdm.position.CounterpartyPositionState;
import com.bank.esps.domain.cdm.position.PositionKey;
import com.bank.esps.domain.cdm.position.PriceQuantity;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.enums.TaxLotMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies a signed position trade to a CDM position state, including the
 * ESPS long-to-short flip that closes one {@code CounterpartyPosition} and
 * opens another.
 */
public final class ApplyPositionTrade {

    private ApplyPositionTrade() {
    }

    public static List<CounterpartyPositionBusinessEvent> apply(CounterpartyPositionState before,
                                                                PositionTrade trade) {
        if (trade == null || trade.getTradeId() == null || trade.getTradeId().isBlank()) {
            throw new IllegalArgumentException("PositionTrade.tradeId is required");
        }
        if (trade.signedQuantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("PositionTrade.quantity must be non-zero");
        }

        PositionDirection currentDirection = resolveDirection(before, trade);
        BigDecimal currentQty = before == null ? BigDecimal.ZERO : before.totalQuantity();
        BigDecimal resultingQty = currentQty.add(trade.signedQuantity());
        boolean wouldFlip = before != null
                && !before.isClosed()
                && !before.getCounterpartyPosition().openLots().isEmpty()
                && ((currentDirection == PositionDirection.LONG && resultingQty.compareTo(BigDecimal.ZERO) < 0)
                || (currentDirection == PositionDirection.SHORT && resultingQty.compareTo(BigDecimal.ZERO) > 0));

        if (wouldFlip) {
            return flipDirection(before, trade, currentDirection, resultingQty);
        }
        return List.of(applySingle(before, trade, currentDirection, ReconciliationStatus.RECONCILED));
    }

    private static List<CounterpartyPositionBusinessEvent> flipDirection(CounterpartyPositionState before,
                                                                         PositionTrade trade,
                                                                         PositionDirection currentDirection,
                                                                         BigDecimal resultingQty) {
        QuantityChangeInstruction closeInstruction = QuantityChangeInstruction.builder()
                .direction(QuantityChangeDirection.DECREASE)
                .change(List.of(changeFrom(trade, before.totalQuantity().abs())))
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        CounterpartyPositionBusinessEvent closeEvent = CreateQuantityChange.apply(
                before, closeInstruction, context(trade, currentDirection, ReconciliationStatus.RECONCILED));
        closeEvent.setIntent(PositionEventIntent.DECREASE);
        closeEvent.setEventQualifier("DIRECTION_FLIP_CLOSE");

        PositionDirection opposite = currentDirection.opposite();
        PositionTrade residual = copyTrade(trade);
        residual.setQuantity(resultingQty);
        CounterpartyPositionBusinessEvent openEvent = applySingle(
                null, residual, opposite, ReconciliationStatus.RECONCILED);
        openEvent.setEventQualifier("DIRECTION_FLIP_OPEN");
        return List.of(closeEvent, openEvent);
    }

    private static CounterpartyPositionBusinessEvent applySingle(CounterpartyPositionState before,
                                                                 PositionTrade trade,
                                                                 PositionDirection direction,
                                                                 ReconciliationStatus reconciliationStatus) {
        QuantityChangeInstruction instruction = QuantityChangeInstruction.builder()
                .direction(trade.changeDirection(direction))
                .change(List.of(changeFrom(trade, trade.signedQuantity().abs())))
                .taxLotMethod(trade.getTaxLotMethod())
                .build();
        return CreateQuantityChange.apply(before, instruction, context(trade, direction, reconciliationStatus));
    }

    private static CreateQuantityChange.QuantityChangeContext context(PositionTrade trade,
                                                                      PositionDirection direction,
                                                                      ReconciliationStatus reconciliationStatus) {
        return new CreateQuantityChange.QuantityChangeContext(
                trade.getTradeId(),
                trade.getAccountId(),
                trade.getBookId(),
                trade.getPartyId() != null ? trade.getPartyId() : trade.getAccountId(),
                trade.getInstrumentId(),
                trade.getCurrency(),
                trade.getContractId(),
                direction,
                trade.getTaxLotMethod(),
                reconciliationStatus
        );
    }

    private static PriceQuantity changeFrom(PositionTrade trade, BigDecimal absoluteQuantity) {
        LocalDate settlement = trade.getSettlementDate() != null ? trade.getSettlementDate() : trade.getTradeDate();
        LocalDate effective = trade.getEffectiveDate() != null ? trade.getEffectiveDate() : settlement;
        PriceQuantity priceQuantity = PriceQuantity.of(
                absoluteQuantity,
                trade.getPrice(),
                trade.getCurrency(),
                trade.getTradeDate(),
                settlement);
        priceQuantity.setEffectiveDate(effective);
        return priceQuantity;
    }

    private static PositionDirection resolveDirection(CounterpartyPositionState before, PositionTrade trade) {
        if (before != null && before.getCounterpartyPosition() != null
                && before.getCounterpartyPosition().getDirection() != null
                && !before.isClosed()) {
            return before.getCounterpartyPosition().getDirection();
        }
        return trade.impliedDirection();
    }

    private static PositionTrade copyTrade(PositionTrade trade) {
        return PositionTrade.builder()
                .tradeId(trade.getTradeId())
                .accountId(trade.getAccountId())
                .bookId(trade.getBookId())
                .partyId(trade.getPartyId())
                .instrumentId(trade.getInstrumentId())
                .currency(trade.getCurrency())
                .contractId(trade.getContractId())
                .positionKey(trade.getPositionKey())
                .quantity(trade.getQuantity())
                .price(trade.getPrice())
                .tradeDate(trade.getTradeDate())
                .effectiveDate(trade.getEffectiveDate())
                .settlementDate(trade.getSettlementDate())
                .tradeType(trade.getTradeType())
                .taxLotMethod(trade.getTaxLotMethod())
                .build();
    }

    public static PositionKey positionKeyFor(PositionTrade trade) {
        return PositionKey.of(trade.getAccountId(), trade.getInstrumentId(),
                trade.getCurrency(), trade.impliedDirection());
    }
}
