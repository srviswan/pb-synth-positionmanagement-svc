package com.bank.esps.domain.contract;

import com.bank.esps.domain.model.ContractRules;

import java.util.concurrent.CompletableFuture;

/**
 * Abstraction for Contract Service integration
 * Allows switching between real Contract Service and mock implementation for testing
 */
public interface ContractService {
    
    /**
     * Get contract rules for a contract ID
     * @param contractId The contract identifier
     * @return ContractRules for the contract, or default FIFO rules if not found
     */
    CompletableFuture<ContractRules> getContractRules(String contractId);
    
    /**
     * Get contract rules synchronously (for hotpath)
     * @param contractId The contract identifier
     * @return ContractRules for the contract, or default FIFO rules if not found
     */
    ContractRules getContractRulesSync(String contractId);
    
    /**
     * Update contract rules (called when contract events are received)
     * @param contractId The contract identifier
     * @param rules The updated contract rules
     * @return CompletableFuture that completes when update is done
     */
    CompletableFuture<Void> updateContractRules(String contractId, ContractRules rules);
    
    /**
     * Check if contract service is available
     * @return true if service is available, false otherwise
     */
    boolean isAvailable();
}
