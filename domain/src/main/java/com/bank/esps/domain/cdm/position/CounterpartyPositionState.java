package com.bank.esps.domain.cdm.position;

import com.bank.esps.domain.enums.ReconciliationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Position plus lifecycle state. Aligns with CDM {@code CounterpartyPositionState}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterpartyPositionState {
    private CounterpartyPosition counterpartyPosition;
    private LifecycleState state;
    @Builder.Default
    private List<Valuation> valuationHistory = new ArrayList<>();
    private int version;
    private ReconciliationStatus reconciliationStatus;
    @Builder.Default
    private BigDecimal realizedPnL = BigDecimal.ZERO;

    public static CounterpartyPositionState open(CounterpartyPosition position) {
        return CounterpartyPositionState.builder()
                .counterpartyPosition(position)
                .state(LifecycleState.formed())
                .version(0)
                .reconciliationStatus(ReconciliationStatus.RECONCILED)
                .realizedPnL(BigDecimal.ZERO)
                .build();
    }

    public String positionKey() {
        return counterpartyPosition != null ? counterpartyPosition.positionKey() : null;
    }

    public BigDecimal totalQuantity() {
        return counterpartyPosition != null ? counterpartyPosition.totalQuantity() : BigDecimal.ZERO;
    }

    public boolean isClosed() {
        return state != null && state.isClosed();
    }

    public void close(ClosedState closedState) {
        this.state = LifecycleState.closed(closedState);
        this.state.validate();
    }

    public void reopen(String newUpi) {
        if (counterpartyPosition != null && newUpi != null) {
            counterpartyPosition.replaceIdentifier(PositionIdentifier.upi(newUpi));
        }
        this.state = LifecycleState.formed();
    }

    public void addRealizedPnL(BigDecimal amount) {
        this.realizedPnL = (realizedPnL != null ? realizedPnL : BigDecimal.ZERO)
                .add(amount != null ? amount : BigDecimal.ZERO);
    }

    public CounterpartyPositionState copy() {
        CounterpartyPosition source = this.counterpartyPosition;
        List<TradeLot> copiedLots = new ArrayList<>();
        if (source != null && source.getTradeLot() != null) {
            for (TradeLot lot : source.getTradeLot()) {
                copiedLots.add(TradeLot.builder()
                        .lotIdentifier(lot.getLotIdentifier())
                        .priceQuantity(lot.getPriceQuantity() == null ? new ArrayList<>() : new ArrayList<>(lot.getPriceQuantity()))
                        .originalQuantity(lot.getOriginalQuantity())
                        .remainingQuantity(lot.getRemainingQuantity())
                        .costBasis(lot.getCostBasis())
                        .currentRefPrice(lot.getCurrentRefPrice())
                        .tradeDate(lot.getTradeDate())
                        .settlementDate(lot.getSettlementDate())
                        .settledQuantity(lot.getSettledQuantity())
                        .acquisitionSequence(lot.getAcquisitionSequence())
                        .build());
            }
        }
        CounterpartyPosition copiedPosition = source == null ? null : CounterpartyPosition.builder()
                .positionIdentifier(source.getPositionIdentifier() == null ? new ArrayList<>() : new ArrayList<>(source.getPositionIdentifier()))
                .openDateTime(source.getOpenDateTime())
                .party(source.getParty() == null ? new ArrayList<>() : new ArrayList<>(source.getParty()))
                .account(source.getAccount())
                .book(source.getBook())
                .product(source.getProduct())
                .direction(source.getDirection())
                .taxLotMethod(source.getTaxLotMethod())
                .tradeLot(copiedLots)
                .priceQuantitySchedule(source.getPriceQuantitySchedule() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(source.getPriceQuantitySchedule()))
                .build();
        return CounterpartyPositionState.builder()
                .counterpartyPosition(copiedPosition)
                .state(state == null ? null : LifecycleState.builder()
                        .positionStatus(state.getPositionStatus())
                        .closedState(state.getClosedState())
                        .build())
                .valuationHistory(valuationHistory == null ? new ArrayList<>() : new ArrayList<>(valuationHistory))
                .version(version)
                .reconciliationStatus(reconciliationStatus)
                .realizedPnL(realizedPnL)
                .build();
    }
}
