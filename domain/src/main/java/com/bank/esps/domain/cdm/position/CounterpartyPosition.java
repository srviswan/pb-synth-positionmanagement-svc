package com.bank.esps.domain.cdm.position;

import com.bank.esps.domain.cdm.base.Account;
import com.bank.esps.domain.cdm.base.Book;
import com.bank.esps.domain.cdm.base.Party;
import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.product.Product;
import com.bank.esps.domain.enums.TaxLotMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accumulated effect of trades in one product for one account. Aligns with
 * CDM {@code CounterpartyPosition}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterpartyPosition {
    @Builder.Default
    private List<PositionIdentifier> positionIdentifier = new ArrayList<>();
    private LocalDateTime openDateTime;
    @Builder.Default
    private List<Party> party = new ArrayList<>();
    private Account account;
    private Book book;
    private Product product;
    private PositionDirection direction;
    @Builder.Default
    private TaxLotMethod taxLotMethod = TaxLotMethod.FIFO;
    @Builder.Default
    private List<TradeLot> tradeLot = new ArrayList<>();
    @Builder.Default
    private List<PriceQuantity> priceQuantitySchedule = new ArrayList<>();

    public String positionKey() {
        return identifierValue(com.bank.esps.domain.cdm.base.IdentifierType.POSITION_KEY)
                .orElse(null);
    }

    public String upi() {
        return identifierValue(com.bank.esps.domain.cdm.base.IdentifierType.UTI)
                .orElse(null);
    }

    public Optional<String> identifierValue(com.bank.esps.domain.cdm.base.IdentifierType type) {
        if (positionIdentifier == null) {
            return Optional.empty();
        }
        return positionIdentifier.stream()
                .filter(id -> id.getIdentifierType() == type)
                .map(PositionIdentifier::firstValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public void replaceIdentifier(PositionIdentifier identifier) {
        if (positionIdentifier == null) {
            positionIdentifier = new ArrayList<>();
        }
        positionIdentifier.removeIf(existing -> existing.getIdentifierType() == identifier.getIdentifierType());
        positionIdentifier.add(identifier);
    }

    public BigDecimal totalQuantity() {
        return tradeLot.stream()
                .map(TradeLot::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal exposure() {
        return tradeLot.stream()
                .map(TradeLot::remainingNotional)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal weightedAveragePrice() {
        BigDecimal totalQty = totalQuantity();
        if (totalQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalValue = tradeLot.stream()
                .map(lot -> lot.remaining().multiply(lot.getCostBasis() != null ? lot.getCostBasis() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalValue.divide(totalQty, 4, RoundingMode.HALF_UP);
    }

    public List<TradeLot> openLots() {
        return tradeLot.stream().filter(TradeLot::isOpen).toList();
    }

    public boolean isShort() {
        return direction == PositionDirection.SHORT;
    }

    public void recordScheduleEntry(PriceQuantity entry) {
        if (priceQuantitySchedule == null) {
            priceQuantitySchedule = new ArrayList<>();
        }
        priceQuantitySchedule.removeIf(existing ->
                existing.getTradeDate() != null && existing.getTradeDate().equals(entry.getTradeDate()));
        priceQuantitySchedule.add(entry);
        priceQuantitySchedule.sort((left, right) -> {
            java.time.LocalDate leftDate = left.accrualStartDate();
            java.time.LocalDate rightDate = right.accrualStartDate();
            if (leftDate == null && rightDate == null) {
                return 0;
            }
            if (leftDate == null) {
                return 1;
            }
            if (rightDate == null) {
                return -1;
            }
            return leftDate.compareTo(rightDate);
        });
    }
}
