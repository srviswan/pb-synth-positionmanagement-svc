package com.bank.esps.domain.contract;

import com.bank.esps.domain.model.Contract;

import java.util.concurrent.CompletableFuture;

/**
 * Abstraction for Contract Service integration
 * Allows switching between real Contract Service and mock implementation for testing
 */
public interface ContractService {
    
    /**
     * Get contract for a contract ID
     * @param contractId The contract identifier
     * @return Contract for the contract, or default FIFO contract if not found
     */
    CompletableFuture<Contract> getContract(String contractId);
    
    /**
     * Get contract synchronously (for hotpath)
     * @param contractId The contract identifier
     * @return Contract for the contract, or default FIFO contract if not found
     */
    Contract getContractSync(String contractId);
    
    /**
     * Update contract (called when contract events are received)
     * @param contractId The contract identifier
     * @param contract The updated contract
     * @return CompletableFuture that completes when update is done
     */
    CompletableFuture<Void> updateContract(String contractId, Contract contract);
    
    /**
     * Check if contract service is available
     * @return true if service is available, false otherwise
     */
    boolean isAvailable();
}
