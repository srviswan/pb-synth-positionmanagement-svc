package com.bank.esps.domain.model;

import com.bank.esps.domain.enums.TaxLotMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Contract that determines tax lot allocation method and business rules
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract {
    private String contractId;
    private TaxLotMethod taxLotMethod; // FIFO, LIFO, HIFO
    private Map<String, Object> businessRules; // Wash sale rules, etc.
    
    /**
     * Get default contract (FIFO)
     */
    public static Contract defaultContract(String contractId) {
        return Contract.builder()
                .contractId(contractId)
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
    }
}
