package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the current state of a position
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionState {
    private String positionKey;
    private String account;
    private String instrument;
    private String currency;
    private List<TaxLot> openLots;
    private int version;
    
    public PositionState(String positionKey, String account, String instrument, String currency) {
        this.positionKey = positionKey;
        this.account = account;
        this.instrument = instrument;
        this.currency = currency;
        this.openLots = new ArrayList<>();
        this.version = 0;
    }
    
    public BigDecimal getTotalQty() {
        return openLots.stream()
                .map(lot -> lot.getRemainingQty() != null ? lot.getRemainingQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public BigDecimal getExposure() {
        return openLots.stream()
                .map(lot -> {
                    BigDecimal qty = lot.getRemainingQty() != null ? lot.getRemainingQty() : BigDecimal.ZERO;
                    BigDecimal price = lot.getCurrentRefPrice() != null ? lot.getCurrentRefPrice() : BigDecimal.ZERO;
                    return qty.multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
