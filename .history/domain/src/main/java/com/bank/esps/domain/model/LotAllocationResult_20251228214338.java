package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of tax lot allocation/reduction operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotAllocationResult {
    private List<String> allocatedLotIds = new ArrayList<>();
    private BigDecimal totalAllocatedQty = BigDecimal.ZERO;
    private BigDecimal remainingQtyToAllocate = BigDecimal.ZERO;
    private boolean fullyAllocated = true;
    
    public void addAllocation(String lotId, BigDecimal qty) {
        allocatedLotIds.add(lotId);
        totalAllocatedQty = totalAllocatedQty.add(qty);
    }
}
