package com.bank.esps.domain.cdm.mapping;

import com.bank.esps.domain.cdm.base.Account;
import com.bank.esps.domain.cdm.base.Book;
import com.bank.esps.domain.cdm.base.Identifier;
import com.bank.esps.domain.cdm.base.IdentifierType;
import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.event.PositionTrade;
import com.bank.esps.domain.cdm.position.CounterpartyPosition;
import com.bank.esps.domain.cdm.position.CounterpartyPositionState;
import com.bank.esps.domain.cdm.position.LifecycleState;
import com.bank.esps.domain.cdm.position.PositionIdentifier;
import com.bank.esps.domain.cdm.position.PositionStatus;
import com.bank.esps.domain.cdm.position.PriceQuantity;
import com.bank.esps.domain.cdm.position.TradeLot;
import com.bank.esps.domain.cdm.product.Product;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.PriceQuantitySchedule;
import com.bank.esps.domain.model.TaxLot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Bidirectional mapping between the existing ESPS types and the CDM-aligned model.
 */
public final class CdmPositionMapper {

    private CdmPositionMapper() {
    }

    public static PositionTrade toPositionTrade(TradeEvent tradeEvent) {
        if (tradeEvent == null) {
            return null;
        }
        return PositionTrade.builder()
                .tradeId(tradeEvent.getTradeId())
                .accountId(tradeEvent.getAccount())
                .bookId(tradeEvent.getBook())
                .partyId(tradeEvent.getAccount())
                .instrumentId(tradeEvent.getInstrument())
                .currency(tradeEvent.getCurrency())
                .contractId(tradeEvent.getContractId())
                .positionKey(tradeEvent.getPositionKey())
                .quantity(tradeEvent.getQuantity())
                .price(tradeEvent.getPrice())
                .tradeDate(tradeEvent.getTradeDate())
                .effectiveDate(tradeEvent.getEffectiveDate())
                .settlementDate(tradeEvent.getSettlementDate())
                .build();
    }

    public static TradeEvent toTradeEvent(PositionTrade trade) {
        if (trade == null) {
            return null;
        }
        return TradeEvent.builder()
                .tradeId(trade.getTradeId())
                .account(trade.getAccountId())
                .book(trade.getBookId())
                .instrument(trade.getInstrumentId())
                .currency(trade.getCurrency())
                .contractId(trade.getContractId())
                .positionKey(trade.getPositionKey())
                .quantity(trade.getQuantity())
                .price(trade.getPrice())
                .tradeDate(trade.getTradeDate())
                .effectiveDate(trade.getEffectiveDate())
                .settlementDate(trade.getSettlementDate())
                .build();
    }

    public static PositionState toPositionState(CounterpartyPositionState cdmState) {
        if (cdmState == null || cdmState.getCounterpartyPosition() == null) {
            return null;
        }
        CounterpartyPosition position = cdmState.getCounterpartyPosition();
        List<TaxLot> lots = new ArrayList<>();
        for (TradeLot lot : position.getTradeLot()) {
            lots.add(TaxLot.builder()
                    .lotId(lot.lotId())
                    .originalQty(lot.getOriginalQuantity())
                    .remainingQty(lot.getRemainingQuantity())
                    .costBasis(lot.getCostBasis())
                    .currentRefPrice(lot.getCurrentRefPrice())
                    .tradeDate(lot.getTradeDate())
                    .settlementDate(lot.getSettlementDate())
                    .settledQuantity(lot.getSettledQuantity())
                    .build());
        }
        return PositionState.builder()
                .positionKey(position.positionKey())
                .account(position.getAccount() != null ? position.getAccount().getAccountId() : null)
                .book(position.getBook() != null ? position.getBook().getBookId() : null)
                .instrument(position.getProduct() != null ? position.getProduct().instrumentId() : null)
                .currency(position.getProduct() != null ? position.getProduct().getCurrency() : null)
                .contractId(position.getProduct() != null ? position.getProduct().getContractId() : null)
                .openLots(lots)
                .version(cdmState.getVersion())
                .priceQuantitySchedule(toSchedule(position))
                .build();
    }

    public static CounterpartyPositionState toCdmState(PositionState state, PositionDirection direction) {
        if (state == null) {
            return null;
        }
        List<TradeLot> lots = new ArrayList<>();
        if (state.getOpenLots() != null) {
            for (TaxLot lot : state.getOpenLots()) {
                lots.add(TradeLot.builder()
                        .lotIdentifier(lot.getLotId() == null ? null : Identifier.of(IdentifierType.LOT, lot.getLotId()))
                        .priceQuantity(List.of(PriceQuantity.of(
                                lot.getRemainingQty() != null ? lot.getRemainingQty() : BigDecimal.ZERO,
                                lot.getCostBasis() != null ? lot.getCostBasis() : BigDecimal.ZERO,
                                state.getCurrency(),
                                lot.getTradeDate(),
                                lot.getSettlementDate())))
                        .originalQuantity(lot.getOriginalQty())
                        .remainingQuantity(lot.getRemainingQty())
                        .costBasis(lot.getCostBasis())
                        .currentRefPrice(lot.getCurrentRefPrice())
                        .tradeDate(lot.getTradeDate())
                        .settlementDate(lot.getSettlementDate())
                        .settledQuantity(lot.getSettledQuantity())
                        .build());
            }
        }
        CounterpartyPosition position = CounterpartyPosition.builder()
                .positionIdentifier(new ArrayList<>(List.of(PositionIdentifier.positionKey(state.getPositionKey()))))
                .account(Account.of(state.getAccount()))
                .book(state.getBook() == null ? null : Book.of(state.getBook()))
                .product(Product.equity(state.getInstrument(), state.getCurrency()))
                .direction(direction != null ? direction : inferDirection(state))
                .tradeLot(lots)
                .priceQuantitySchedule(fromSchedule(state.getPriceQuantitySchedule()))
                .build();
        if (state.getContractId() != null) {
            position.getProduct().setContractId(state.getContractId());
        }
        boolean closed = state.getTotalQty().compareTo(BigDecimal.ZERO) == 0;
        return CounterpartyPositionState.builder()
                .counterpartyPosition(position)
                .state(closed ? LifecycleState.closed(
                        com.bank.esps.domain.cdm.position.ClosedState.terminated(null))
                        : LifecycleState.formed())
                .version(state.getVersion())
                .build();
    }

    private static PositionDirection inferDirection(PositionState state) {
        return state.getTotalQty().compareTo(BigDecimal.ZERO) < 0
                ? PositionDirection.SHORT
                : PositionDirection.LONG;
    }

    private static PriceQuantitySchedule toSchedule(CounterpartyPosition position) {
        PriceQuantitySchedule schedule = new PriceQuantitySchedule();
        schedule.setCurrency(position.getProduct() != null ? position.getProduct().getCurrency() : null);
        schedule.setUnit("SHARES");
        if (position.getPriceQuantitySchedule() != null) {
            for (PriceQuantity entry : position.getPriceQuantitySchedule()) {
                schedule.getSchedule().add(PriceQuantitySchedule.DatedPriceQuantity.builder()
                        .tradeDate(entry.getTradeDate())
                        .settlementDate(entry.getSettlementDate())
                        .effectiveDate(entry.getEffectiveDate())
                        .quantity(entry.quantityAmount())
                        .settledQuantity(entry.settledQuantityAmount())
                        .price(entry.priceAmount())
                        .build());
            }
        }
        return schedule;
    }

    private static List<PriceQuantity> fromSchedule(PriceQuantitySchedule schedule) {
        List<PriceQuantity> entries = new ArrayList<>();
        if (schedule == null || schedule.getSchedule() == null) {
            return entries;
        }
        for (PriceQuantitySchedule.DatedPriceQuantity dated : schedule.getSchedule()) {
            entries.add(PriceQuantity.builder()
                    .quantity(com.bank.esps.domain.cdm.base.Quantity.ofShares(dated.getQuantity()))
                    .settledQuantity(com.bank.esps.domain.cdm.base.Quantity.ofShares(dated.getSettledQuantity()))
                    .price(com.bank.esps.domain.cdm.base.Price.assetPrice(dated.getPrice(), schedule.getCurrency()))
                    .tradeDate(dated.getTradeDate())
                    .settlementDate(dated.getSettlementDate())
                    .effectiveDate(dated.getEffectiveDate())
                    .build());
        }
        return entries;
    }

    public static PositionStatus toPositionStatus(boolean closed) {
        return closed ? PositionStatus.CLOSED : PositionStatus.FORMED;
    }
}
