package com.bank.esps.domain.cdm.event;

import com.bank.esps.domain.cdm.base.Identifier;
import com.bank.esps.domain.cdm.position.PriceQuantity;
import com.bank.esps.domain.enums.TaxLotMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Inputs for a quantity-change primitive. Aligns with CDM
 * {@code QuantityChangeInstruction}. Quantity on {@code change} is always
 * positive; {@code direction} carries the sign.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuantityChangeInstruction {
    @Builder.Default
    private List<PriceQuantity> change = new ArrayList<>();
    private QuantityChangeDirection direction;
    @Builder.Default
    private List<Identifier> lotIdentifier = new ArrayList<>();
    @Builder.Default
    private TaxLotMethod taxLotMethod = TaxLotMethod.FIFO;

    public PriceQuantity primaryChange() {
        if (change == null || change.isEmpty()) {
            return null;
        }
        return change.get(0);
    }

    public BigDecimal changeQuantity() {
        PriceQuantity primary = primaryChange();
        return primary == null ? BigDecimal.ZERO : primary.quantityAmount().abs();
    }

    public void validate() {
        if (direction == null) {
            throw new IllegalArgumentException("QuantityChangeInstruction.direction is required");
        }
        if (change == null || change.isEmpty()) {
            throw new IllegalArgumentException("QuantityChangeInstruction.change is required");
        }
        if (changeQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("QuantityChangeInstruction.change quantity must be a positive number");
        }
        if ((direction == QuantityChangeDirection.DECREASE || direction == QuantityChangeDirection.REPLACE)
                && lotIdentifier != null && lotIdentifier.size() == 0) {
            // CDM: lotIdentifier is mandatory when decreasing/replacing a multi-lot trade.
            // ESPS allows method-based allocation when lots are omitted.
        }
    }
}
