package com.bank.esps.application.service;

import com.bank.esps.domain.model.ContractRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing contract rules
 * In production, this would integrate with Contract Service
 * For now, uses in-memory cache with defaults
 */
@Service
@Slf4j
public class ContractRulesService {
    
    private final Map<String, ContractRules> rulesCache = new ConcurrentHashMap<>();
    
    /**
     * Get contract rules for a contract ID
     * Defaults to FIFO if not found
     */
    public ContractRules getContractRules(String contractId) {
        if (contractId == null || contractId.isEmpty()) {
            log.warn("Contract ID is null or empty, using default FIFO rules");
            return ContractRules.defaultRules("DEFAULT");
        }
        
        return rulesCache.computeIfAbsent(contractId, id -> {
            log.info("Contract rules not found for {}, using default FIFO rules", id);
            return ContractRules.defaultRules(id);
        });
    }
    
    /**
     * Update contract rules (called when contract events are received)
     */
    public void updateContractRules(ContractRules rules) {
        rulesCache.put(rules.getContractId(), rules);
        log.info("Updated contract rules for contract: {}", rules.getContractId());
    }
    
    /**
     * Clear cache (for testing or cache refresh)
     */
    public void clearCache() {
        rulesCache.clear();
        log.info("Cleared contract rules cache");
    }
}
