package com.bank.esps.domain.model;

import com.bank.esps.domain.enums.TaxLotMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Contract rules that determine tax lot allocation method and business rules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractRules {
    private String contractId;
    private TaxLotMethod taxLotMethod; // FIFO, LIFO, HIFO
    private Map<String, Object> businessRules; // Wash sale rules, etc.
    
    /**
     * Get default contract rules (FIFO)
     */
    public static ContractRules defaultRules(String contractId) {
        return ContractRules.builder()
                .contractId(contractId)
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
    }
}
