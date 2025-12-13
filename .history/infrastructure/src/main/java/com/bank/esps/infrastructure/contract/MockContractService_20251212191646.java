package com.bank.esps.infrastructure.contract;

import com.bank.esps.domain.contract.ContractService;
import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.domain.model.ContractRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mock implementation of ContractService for testing
 * Stores contract rules in-memory
 * Can be configured to return default FIFO rules or custom rules
 */
@Component
@ConditionalOnProperty(
        name = "app.contract.service.type",
        havingValue = "mock",
        matchIfMissing = true  // Default to mock for local development/testing
)
public class MockContractService implements ContractService {
    
    private static final Logger log = LoggerFactory.getLogger(MockContractService.class);
    
    // In-memory storage for contract rules
    private final Map<String, ContractRules> contractRulesCache = new HashMap<>();
    
    public MockContractService() {
        // Initialize with some default test contracts
        initializeDefaultContracts();
    }
    
    /**
     * Initialize with some default test contracts
     */
    private void initializeDefaultContracts() {
        // Contract with FIFO
        contractRulesCache.put("CONTRACT-FIFO", ContractRules.builder()
                .contractId("CONTRACT-FIFO")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build());
        
        // Contract with LIFO
        contractRulesCache.put("CONTRACT-LIFO", ContractRules.builder()
                .contractId("CONTRACT-LIFO")
                .taxLotMethod(TaxLotMethod.LIFO)
                .build());
        
        // Contract with HIFO
        contractRulesCache.put("CONTRACT-HIFO", ContractRules.builder()
                .contractId("CONTRACT-HIFO")
                .taxLotMethod(TaxLotMethod.HIFO)
                .build());
        
        log.info("Initialized MockContractService with {} default contracts", contractRulesCache.size());
    }
    
    @Override
    public CompletableFuture<ContractRules> getContractRules(String contractId) {
        return CompletableFuture.completedFuture(getContractRulesSync(contractId));
    }
    
    @Override
    public ContractRules getContractRulesSync(String contractId) {
        if (contractId == null || contractId.isEmpty()) {
            log.debug("Contract ID is null or empty, using default FIFO rules");
            return ContractRules.defaultRules("DEFAULT");
        }
        
        ContractRules rules = contractRulesCache.get(contractId);
        
        if (rules != null) {
            log.debug("Retrieved contract rules from mock cache for contract {}: method={}", 
                    contractId, rules.getTaxLotMethod());
            return rules;
        } else {
            log.debug("Contract rules not found in mock cache for {}, using default FIFO rules", contractId);
            // Return default FIFO rules
            ContractRules defaultRules = ContractRules.defaultRules(contractId);
            // Cache it for future requests
            contractRulesCache.put(contractId, defaultRules);
            return defaultRules;
        }
    }
    
    @Override
    public CompletableFuture<Void> updateContractRules(String contractId, ContractRules rules) {
        return CompletableFuture.runAsync(() -> {
            contractRulesCache.put(contractId, rules);
            log.info("Updated contract rules in mock cache for contract {}: method={}", 
                    contractId, rules.getTaxLotMethod());
        });
    }
    
    @Override
    public boolean isAvailable() {
        // Mock service is always available
        return true;
    }
    
    /**
     * Clear all cached contract rules (for testing)
     */
    public void clearCache() {
        contractRulesCache.clear();
        initializeDefaultContracts(); // Re-initialize defaults
        log.info("Cleared mock contract rules cache");
    }
    
    /**
     * Get all cached contract rules (for testing/debugging)
     */
    public Map<String, ContractRules> getAllCachedRules() {
        return new HashMap<>(contractRulesCache);
    }
    
    /**
     * Add a contract rule to the mock cache (for testing)
     */
    public void addContractRule(String contractId, ContractRules rules) {
        contractRulesCache.put(contractId, rules);
        log.info("Added contract rule to mock cache: contract={}, method={}", 
                contractId, rules.getTaxLotMethod());
    }
}
