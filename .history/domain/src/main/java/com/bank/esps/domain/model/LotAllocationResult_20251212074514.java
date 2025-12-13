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
            map.put(allocation.getLotId(), Map.of(
                    "qty", allocation.getQty(),
                    "price", allocation.getPrice()
            ));
        }
        return map;
    }
    
    /**
     * Add an allocation
     */
    public void addAllocation(String lotId, BigDecimal qty, BigDecimal price) {
        allocations.add(LotAllocation.builder()
                .lotId(lotId)
                .qty(qty)
                .price(price)
                .build());
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LotAllocation {
        private String lotId;
        private BigDecimal qty;
        private BigDecimal price;
    }
}
