package com.bank.esps.application.service;

import com.bank.esps.domain.model.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing contract rules
 * Provides default FIFO rules if contract not found
 */
@Service
public class ContractRulesService {
    
    private static final Logger log = LoggerFactory.getLogger(ContractRulesService.class);
    
    // In-memory cache for contract rules (can be replaced with Redis/external service)
    private final Map<String, Contract> contractCache = new ConcurrentHashMap<>();
    
    /**
     * Get contract rules for a contract ID
     * Returns default FIFO rules if contract not found
     */
    public Contract getContract(String contractId) {
        if (contractId == null || contractId.isEmpty()) {
            return getDefaultContract();
        }
        
        Contract contract = contractCache.get(contractId);
        if (contract == null) {
            log.debug("Contract {} not found in cache, using default FIFO rules", contractId);
            return getDefaultContract();
        }
        
        return contract;
    }
    
    /**
     * Update or create contract rules
     */
    public void updateContract(Contract contract) {
        if (contract != null && contract.getContractId() != null) {
            contractCache.put(contract.getContractId(), contract);
            log.debug("Updated contract rules: contractId={}, method={}", 
                    contract.getContractId(), contract.getTaxLotMethod());
        }
    }
    
    /**
     * Get default contract with FIFO rules
     */
    private Contract getDefaultContract() {
        return Contract.builder()
                .contractId("DEFAULT")
                .taxLotMethod("FIFO")
                .build();
    }
    
    /**
     * Get tax lot method from contract
     */
    public LotLogic.TaxLotMethod getTaxLotMethod(String contractId) {
        Contract contract = getContract(contractId);
        String method = contract.getTaxLotMethod();
        
        if (method == null || method.isEmpty()) {
            return LotLogic.TaxLotMethod.FIFO;
        }
        
        try {
            return LotLogic.TaxLotMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown tax lot method: {}, defaulting to FIFO", method);
            return LotLogic.TaxLotMethod.FIFO;
        }
    }
}
