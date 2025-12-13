package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates tax lots and provides position-level operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionState {
    @Builder.Default
    private List<TaxLot> openLots = new ArrayList<>();
    
    /**
     * Get all open lots (non-zero remaining quantity)
     */
    public List<TaxLot> getOpenLots() {
        return openLots.stream()
                .filter(lot -> !lot.isClosed())
                .collect(Collectors.toList());
    }
    
    /**
     * Get total quantity across all open lots
     */
    public BigDecimal getTotalQty() {
        return openLots.stream()
                .map(TaxLot::getRemainingQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Get exposure (sum of quantity * price)
     */
    public BigDecimal getExposure() {
        return openLots.stream()
                .map(lot -> lot.getRemainingQty().multiply(lot.getCurrentRefPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Get lot count
     */
    public int getLotCount() {
        return openLots.size();
    }
    
    /**
     * Add a new lot
     */
    public void addLot(TaxLot lot) {
        openLots.add(lot);
    }
    
    /**
     * Remove a lot (when fully closed)
     */
    public void removeLot(String lotId) {
        openLots.removeIf(lot -> lot.getId().equals(lotId));
    }
    
    /**
     * Update lot
     */
    public void updateLot(TaxLot updatedLot) {
        for (int i = 0; i < openLots.size(); i++) {
            if (openLots.get(i).getId().equals(updatedLot.getId())) {
                openLots.set(i, updatedLot);
                return;
            }
        }
    }
    
    /**
     * Find lot by ID
     */
    public TaxLot findLotById(String lotId) {
        return openLots.stream()
                .filter(lot -> lot.getId().equals(lotId))
                .findFirst()
                .orElse(null);
    }
}
