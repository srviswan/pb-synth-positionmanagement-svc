package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.event.PositionTrade;
import com.bank.esps.domain.cdm.product.FinancialContract;
import com.bank.esps.domain.cdm.repository.BasketActivityRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the current basket, allocates a hedge, and saves the resulting state.
 * This is the state-saving replacement for event-store append + replay.
 */
public final class SaveBasketActivity {

    private SaveBasketActivity() {
    }

    public static List<BasketActivity> allocateAndSave(BasketActivityRepository repository,
                                                       FinancialContract contract,
                                                       PositionTrade trade) {
        if (repository == null) {
            throw new IllegalArgumentException("BasketActivityRepository is required");
        }
        if (contract == null || contract.getContractId() == null) {
            throw new IllegalArgumentException("FinancialContract is required");
        }
        String securityId = trade.getInstrumentId() != null && !trade.getInstrumentId().isBlank()
                ? trade.getInstrumentId()
                : (contract.getProduct() != null ? contract.getProduct().underlierId() : null);
        PositionDirection implied = trade.impliedDirection();
        BasketActivity current = repository.findOpenByContract(contract.getContractId(), securityId, implied)
                .or(() -> repository.findOpenByContract(contract.getContractId(), securityId, implied.opposite()))
                .or(() -> repository.findLatestByContract(contract.getContractId(), securityId, implied))
                .orElse(null);

        List<BasketActivity> saved = new ArrayList<>();
        if (current != null && wouldFlip(current, trade)) {
            BigDecimal residual = current.totalQuantity().add(trade.signedQuantity());
            PositionTrade closeRemainder = copy(trade);
            closeRemainder.setQuantity(current.isShort()
                    ? current.totalQuantity().abs()
                    : current.totalQuantity().abs().negate());
            BasketActivity closed = AllocateHedgeToBasket.allocate(current, contract, closeRemainder);
            repository.save(closed);
            saved.add(closed);

            PositionTrade residualTrade = copy(trade);
            residualTrade.setQuantity(residual);
            BasketActivity opened = AllocateHedgeToBasket.allocate(null, contract, residualTrade);
            repository.save(opened);
            saved.add(opened);
            return saved;
        }

        BasketActivity after = AllocateHedgeToBasket.allocate(current, contract, trade);
        repository.save(after);
        saved.add(after);
        return saved;
    }

    private static boolean wouldFlip(BasketActivity current, PositionTrade trade) {
        if (current == null || current.isClosed() || current.getOpenLots().isEmpty()) {
            return false;
        }
        BigDecimal resulting = current.totalQuantity().add(trade.signedQuantity());
        return (current.getDirection() == PositionDirection.LONG && resulting.compareTo(BigDecimal.ZERO) < 0)
                || (current.getDirection() == PositionDirection.SHORT && resulting.compareTo(BigDecimal.ZERO) > 0);
    }

    private static PositionTrade copy(PositionTrade trade) {
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
}
