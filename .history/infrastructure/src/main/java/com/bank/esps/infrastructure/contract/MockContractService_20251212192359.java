package com.bank.esps.infrastructure.contract;

import com.bank.esps.domain.contract.ContractService;
import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.domain.model.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mock implementation of ContractService for testing and local development
 * 
 * Features:
 * - In-memory storage of contracts
 * - Pre-configured with test contracts (FIFO, LIFO, HIFO)
 * - Test utilities for adding/removing contracts
 * - Always available (no network calls)
 * 
 * Configuration:
 * - app.contract.service.type: "mock" (default)
 * 
 * Usage in tests:
 * ```java
 * @Autowired
 * private MockContractService mockContractService;
 * 
 * mockContractService.addContract("CUSTOM-123", contract);
 * Contract contract = mockContractService.getContractSync("CUSTOM-123");
 * ```
 */
@Component
@ConditionalOnProperty(
        name = "app.contract.service.type",
        havingValue = "mock",
        matchIfMissing = true  // Default to mock for local development/testing
)
public class MockContractService implements ContractService {
    
    private static final Logger log = LoggerFactory.getLogger(MockContractService.class);
    
    // In-memory storage for contracts
    private final Map<String, Contract> contractCache = new HashMap<>();
    
    public MockContractService() {
        // Initialize with some default test contracts
        initializeDefaultContracts();
    }
    
    /**
     * Initialize with some default test contracts
     */
    private void initializeDefaultContracts() {
        // Contract with FIFO
        contractCache.put("CONTRACT-FIFO", Contract.builder()
                .contractId("CONTRACT-FIFO")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build());
        
        // Contract with LIFO
        contractCache.put("CONTRACT-LIFO", Contract.builder()
                .contractId("CONTRACT-LIFO")
                .taxLotMethod(TaxLotMethod.LIFO)
                .build());
        
        // Contract with HIFO
        contractCache.put("CONTRACT-HIFO", Contract.builder()
                .contractId("CONTRACT-HIFO")
                .taxLotMethod(TaxLotMethod.HIFO)
                .build());
        
        log.info("Initialized MockContractService with {} default contracts", contractCache.size());
    }
    
    @Override
    public CompletableFuture<Contract> getContract(String contractId) {
        return CompletableFuture.completedFuture(getContractSync(contractId));
    }
    
    @Override
    public Contract getContractSync(String contractId) {
        if (contractId == null || contractId.isEmpty()) {
            log.debug("Contract ID is null or empty, using default FIFO contract");
            return Contract.defaultContract("DEFAULT");
        }
        
        Contract contract = contractCache.get(contractId);
        
        if (contract != null) {
            log.debug("Retrieved contract from mock cache for contract {}: method={}", 
                    contractId, contract.getTaxLotMethod());
            return contract;
        } else {
            log.debug("Contract not found in mock cache for {}, using default FIFO contract", contractId);
            // Return default FIFO contract
            Contract defaultContract = Contract.defaultContract(contractId);
            // Cache it for future requests
            contractCache.put(contractId, defaultContract);
            return defaultContract;
        }
    }
    
    @Override
    public CompletableFuture<Void> updateContract(String contractId, Contract contract) {
        return CompletableFuture.runAsync(() -> {
            contractCache.put(contractId, contract);
            log.info("Updated contract in mock cache for contract {}: method={}", 
                    contractId, contract.getTaxLotMethod());
        });
    }
    
    @Override
    public boolean isAvailable() {
        // Mock service is always available
        return true;
    }
    
    /**
     * Clear all cached contracts (for testing)
     */
    public void clearCache() {
        contractCache.clear();
        initializeDefaultContracts(); // Re-initialize defaults
        log.info("Cleared mock contract cache");
    }
    
    /**
     * Get all cached contracts (for testing/debugging)
     */
    public Map<String, Contract> getAllCachedContracts() {
        return new HashMap<>(contractCache);
    }
    
    /**
     * Add a contract to the mock cache (for testing)
     */
    public void addContract(String contractId, Contract contract) {
        contractCache.put(contractId, contract);
        log.info("Added contract to mock cache: contract={}, method={}", 
                contractId, contract.getTaxLotMethod());
    }
}
