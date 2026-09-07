package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.base.Account;
import com.bank.esps.domain.cdm.base.Book;
import com.bank.esps.domain.cdm.base.Party;
import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.event.CounterpartyPositionBusinessEvent;
import com.bank.esps.domain.cdm.event.Instruction;
import com.bank.esps.domain.cdm.event.PositionEventIntent;
import com.bank.esps.domain.cdm.event.PrimitiveInstruction;
import com.bank.esps.domain.cdm.event.QuantityChangeDirection;
import com.bank.esps.domain.cdm.event.QuantityChangeInstruction;
import com.bank.esps.domain.cdm.position.ClosedState;
import com.bank.esps.domain.cdm.position.CounterpartyPosition;
import com.bank.esps.domain.cdm.position.CounterpartyPositionState;
import com.bank.esps.domain.cdm.position.PositionIdentifier;
import com.bank.esps.domain.cdm.position.PositionKey;
import com.bank.esps.domain.cdm.position.PositionStatus;
import com.bank.esps.domain.cdm.position.PriceQuantity;
import com.bank.esps.domain.cdm.position.TradeLot;
import com.bank.esps.domain.cdm.product.Product;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.enums.TaxLotMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies a quantity-change primitive and produces a position business event.
 * Aligns with CDM {@code Create_QuantityChange} and
 * {@code CounterpartyPositionBusinessEvent}.
 */
public final class CreateQuantityChange {

    private CreateQuantityChange() {
    }

    public static CounterpartyPositionBusinessEvent apply(CounterpartyPositionState before,
                                                          QuantityChangeInstruction instruction,
                                                          QuantityChangeContext context) {
        instruction.validate();
        PositionEventIntent intent = QualifyPositionEvent.qualify(before, instruction);
        PriceQuantity change = instruction.primaryChange();
        CounterpartyPositionState working = ensureBefore(before, context, change);
        CounterpartyPositionState after = working.copy();
        after.setVersion(working.getVersion() + 1);
        after.setReconciliationStatus(context.reconciliationStatus());

        applyDirection(after, instruction, context);

        if (after.totalQuantity().compareTo(BigDecimal.ZERO) == 0) {
            after.close(ClosedState.terminated(change.accrualStartDate()));
        } else if (after.getState() != null && after.getState().getPositionStatus() == PositionStatus.CLOSED) {
            after.reopen(context.tradeId());
        }

        recordSchedule(after, change);

        CounterpartyPositionBusinessEvent event = CounterpartyPositionBusinessEvent.builder()
                .intent(intent)
                .eventQualifier(intent.name())
                .eventDate(change.getTradeDate())
                .effectiveDate(change.getEffectiveDate() != null ? change.getEffectiveDate() : change.getSettlementDate())
                .instruction(Instruction.builder()
                        .primitiveInstruction(PrimitiveInstruction.quantityChange(instruction))
                        .before(before)
                        .build())
                .after(new ArrayList<>(List.of(after)))
                .build();
        event.validate();
        return event;
    }

    private static void applyDirection(CounterpartyPositionState after,
                                       QuantityChangeInstruction instruction,
                                       QuantityChangeContext context) {
        PriceQuantity change = instruction.primaryChange();
        BigDecimal qty = instruction.changeQuantity();
        BigDecimal price = change.priceAmount();
        TaxLotMethod method = instruction.getTaxLotMethod() != null
                ? instruction.getTaxLotMethod()
                : after.getCounterpartyPosition().getTaxLotMethod();
        boolean shortPosition = after.getCounterpartyPosition().isShort();

        switch (instruction.getDirection()) {
            case INCREASE -> {
                if (after.isClosed()) {
                    after.reopen(context.tradeId());
                    after.getCounterpartyPosition().getTradeLot().clear();
                }
                TradeLot newLot = TradeLot.open(signedQuantity(qty, shortPosition), price,
                        change.getPrice() != null ? change.getPrice().getCurrency() : context.currency(),
                        change.getTradeDate(), change.getSettlementDate());
                newLot.setAcquisitionSequence(after.getCounterpartyPosition().getTradeLot().size());
                after.getCounterpartyPosition().getTradeLot().add(newLot);
            }
            case DECREASE -> {
                List<TradeLot.LotReduction> reductions = LotAllocation.reduce(
                        after.getCounterpartyPosition().getTradeLot(), qty, method, price, shortPosition);
                reductions.forEach(reduction -> after.addRealizedPnL(reduction.realizedPnL()));
            }
            case REPLACE -> replaceLots(after, qty, price, change, shortPosition);
        }
    }

    private static void replaceLots(CounterpartyPositionState after, BigDecimal targetQty, BigDecimal price,
                                    PriceQuantity change, boolean shortPosition) {
        BigDecimal current = after.totalQuantity().abs();
        if (targetQty.compareTo(current) > 0) {
            BigDecimal increase = targetQty.subtract(current);
            TradeLot newLot = TradeLot.open(signedQuantity(increase, shortPosition), price,
                    change.getPrice() != null ? change.getPrice().getCurrency() : null,
                    change.getTradeDate(), change.getSettlementDate());
            newLot.setAcquisitionSequence(after.getCounterpartyPosition().getTradeLot().size());
            after.getCounterpartyPosition().getTradeLot().add(newLot);
        } else if (targetQty.compareTo(current) < 0) {
            LotAllocation.reduce(after.getCounterpartyPosition().getTradeLot(),
                    current.subtract(targetQty),
                    after.getCounterpartyPosition().getTaxLotMethod(),
                    price, shortPosition);
        }
    }

    private static BigDecimal signedQuantity(BigDecimal quantity, boolean shortPosition) {
        return shortPosition ? quantity.abs().negate() : quantity.abs();
    }

    private static void recordSchedule(CounterpartyPositionState after, PriceQuantity change) {
        String currency = after.getCounterpartyPosition().getProduct() != null
                ? after.getCounterpartyPosition().getProduct().getCurrency()
                : null;
        after.getCounterpartyPosition().recordScheduleEntry(PriceQuantity.builder()
                .quantity(com.bank.esps.domain.cdm.base.Quantity.ofShares(after.totalQuantity()))
                .settledQuantity(com.bank.esps.domain.cdm.base.Quantity.ofShares(after.totalQuantity()))
                .price(com.bank.esps.domain.cdm.base.Price.assetPrice(
                        after.getCounterpartyPosition().weightedAveragePrice(), currency))
                .tradeDate(change.getTradeDate())
                .settlementDate(change.getSettlementDate())
                .effectiveDate(change.getEffectiveDate() != null ? change.getEffectiveDate() : change.getSettlementDate())
                .build());
    }

    private static CounterpartyPositionState ensureBefore(CounterpartyPositionState before,
                                                          QuantityChangeContext context,
                                                          PriceQuantity change) {
        if (before != null && before.getCounterpartyPosition() != null) {
            return before;
        }
        PositionKey key = PositionKey.of(context.accountId(), context.instrumentId(),
                context.currency(), context.direction());
        CounterpartyPosition position = CounterpartyPosition.builder()
                .positionIdentifier(new ArrayList<>(List.of(
                        PositionIdentifier.positionKey(key.getValue()),
                        PositionIdentifier.upi(context.tradeId()))))
                .openDateTime(LocalDateTime.now())
                .party(context.partyId() == null ? new ArrayList<>() : new ArrayList<>(List.of(Party.of(context.partyId()))))
                .account(Account.of(context.accountId()))
                .book(context.bookId() == null ? null : Book.of(context.bookId()))
                .product(Product.equity(context.instrumentId(), context.currency()))
                .direction(context.direction())
                .taxLotMethod(context.taxLotMethod())
                .tradeLot(new ArrayList<>())
                .priceQuantitySchedule(new ArrayList<>())
                .build();
        if (context.contractId() != null) {
            position.getProduct().setContractId(context.contractId());
        }
        return CounterpartyPositionState.open(position);
    }

    public record QuantityChangeContext(
            String tradeId,
            String accountId,
            String bookId,
            String partyId,
            String instrumentId,
            String currency,
            String contractId,
            PositionDirection direction,
            TaxLotMethod taxLotMethod,
            ReconciliationStatus reconciliationStatus
    ) {
        public QuantityChangeContext {
            direction = direction != null ? direction : PositionDirection.LONG;
            taxLotMethod = taxLotMethod != null ? taxLotMethod : TaxLotMethod.FIFO;
            reconciliationStatus = reconciliationStatus != null
                    ? reconciliationStatus
                    : ReconciliationStatus.RECONCILED;
        }

        public static QuantityChangeContext of(String tradeId, String accountId, String instrumentId, String currency) {
            return new QuantityChangeContext(tradeId, accountId, null, accountId, instrumentId, currency,
                    null, PositionDirection.LONG, TaxLotMethod.FIFO, ReconciliationStatus.RECONCILED);
        }
    }
}
