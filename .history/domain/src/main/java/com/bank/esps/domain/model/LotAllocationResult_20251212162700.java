package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks which lots were allocated/reduced for audit trail
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotAllocationResult {
    @Builder.Default
    private List<LotAllocation> allocations = new ArrayList<>();
    
    /**
     * Get allocations as map for JSON serialization
     */
    public Map<String, Object> getAllocationsMap() {
        Map<String, Object> map = new HashMap<>();
        for (LotAllocation allocation : allocations) {
            Map<String, Object> allocationMap = new HashMap<>();
            allocationMap.put("qty", allocation.getQty());
            allocationMap.put("price", allocation.getPrice());
            if (allocation.getRealizedPnL() != null) {
                allocationMap.put("realizedPnL", allocation.getRealizedPnL());
            }
            map.put(allocation.getLotId(), allocationMap);
        }
        return map;
    }
    
    /**
     * Add an allocation (for adding lots)
     */
    public void addAllocation(String lotId, BigDecimal qty, BigDecimal price) {
        allocations.add(LotAllocation.builder()
                .lotId(lotId)
                .qty(qty)
                .price(price)
                .build());
    }
    
    /**
     * Add a reduction with P&L (for closing/reducing lots)
     */
    public void addReduction(String lotId, BigDecimal qty, BigDecimal closePrice, BigDecimal realizedPnL) {
        allocations.add(LotAllocation.builder()
                .lotId(lotId)
                .qty(qty)
                .price(closePrice)
                .realizedPnL(realizedPnL)
                .build());
    }
    
    /**
     * Get total realized P&L from all allocations
     */
    public BigDecimal getTotalRealizedPnL() {
        return allocations.stream()
                .filter(a -> a.getRealizedPnL() != null)
                .map(LotAllocation::getRealizedPnL)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LotAllocation {
        private String lotId;
        private BigDecimal qty;
        private BigDecimal price;
        private BigDecimal realizedPnL; // P&L when closing/reducing this lot
    }
}
