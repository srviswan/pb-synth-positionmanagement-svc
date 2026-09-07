package com.bank.esps.domain.cdm.basket;

import com.bank.esps.domain.cdm.base.Account;
import com.bank.esps.domain.cdm.base.Book;
import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.position.ClosedState;
import com.bank.esps.domain.cdm.position.LifecycleState;
import com.bank.esps.domain.cdm.position.PositionKey;
import com.bank.esps.domain.cdm.position.PositionStatus;
import com.bank.esps.domain.cdm.product.FinancialContract;
import com.bank.esps.domain.cdm.product.FinancialProduct;
import com.bank.esps.domain.enums.TaxLotMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Current state of hedge trades allocated to a contract.
 * Maps to the firm's {@code DOBasketActivity} / {@code DOBasketActivityTable}.
 *
 * <p>This is a state-saving aggregate: the header plus details, open lots,
 * closing lots, and settlements are persisted as-is. History is the child
 * rows, not an event stream replay.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketActivity {
    private String activityId;
    private String contractId;
    private FinancialProduct product;
    private Account account;
    private Book book;
    private PositionDirection direction;
    private String positionKey;
    private String upi;
    @Builder.Default
    private TaxLotMethod taxLotMethod = TaxLotMethod.FIFO;
    @Builder.Default
    private LifecycleState lifecycle = LifecycleState.formed();
    @Builder.Default
    private int version = 0;
    @Builder.Default
    private BigDecimal realizedPnL = BigDecimal.ZERO;
    private OffsetDateTime updatedAt;

    @Builder.Default
    private List<BasketActivityDetail> details = new ArrayList<>();
    @Builder.Default
    private List<OpenLot> openLots = new ArrayList<>();
    @Builder.Default
    private List<ClosingLot> closingLots = new ArrayList<>();
    @Builder.Default
    private List<BasketSettlement> settlements = new ArrayList<>();

    public static BasketActivity open(FinancialContract contract, PositionDirection direction) {
        FinancialProduct product = contract.getProduct();
        String underlierId = product != null ? product.underlierId() : null;
        String currency = product != null ? product.getCurrency() : null;
        String accountId = contract.getAccount() != null ? contract.getAccount().getAccountId() : null;
        PositionKey key = PositionKey.of(accountId, underlierId, currency, direction);
        return BasketActivity.builder()
                .activityId(UUID.randomUUID().toString())
                .contractId(contract.getContractId())
                .product(product)
                .account(contract.getAccount())
                .book(contract.getBook())
                .direction(direction != null ? direction : PositionDirection.LONG)
                .positionKey(key.getValue())
                .taxLotMethod(contract.getTaxLotMethod())
                .lifecycle(LifecycleState.formed())
                .version(0)
                .realizedPnL(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .openLots(new ArrayList<>())
                .closingLots(new ArrayList<>())
                .settlements(new ArrayList<>())
                .build();
    }

    public BigDecimal totalQuantity() {
        return openLots.stream()
                .map(OpenLot::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isClosed() {
        return lifecycle != null && lifecycle.isClosed();
    }

    public boolean isShort() {
        return direction == PositionDirection.SHORT;
    }

    public void closeIfFlat(LocalDate effectiveDate) {
        if (totalQuantity().compareTo(BigDecimal.ZERO) == 0) {
            this.lifecycle = LifecycleState.closed(ClosedState.terminated(effectiveDate));
        }
    }

    public void reopen(String newUpi) {
        this.upi = newUpi;
        this.lifecycle = LifecycleState.formed();
        this.openLots.clear();
    }

    public PositionStatus status() {
        return lifecycle != null ? lifecycle.getPositionStatus() : PositionStatus.FORMED;
    }
}
