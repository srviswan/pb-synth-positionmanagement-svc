package com.bank.esps.domain.cdm.repository;

import com.bank.esps.domain.cdm.product.FinancialContract;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process contract store used by tests and the v1 API when JPA is not wired.
 */
public class InMemoryFinancialContractRepository implements FinancialContractRepository {

    private final Map<String, FinancialContract> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<FinancialContract> findByContractId(String contractId) {
        return Optional.ofNullable(byId.get(contractId));
    }

    @Override
    public void save(FinancialContract contract) {
        if (contract == null || contract.getContractId() == null || contract.getContractId().isBlank()) {
            throw new IllegalArgumentException("FinancialContract.contractId is required");
        }
        byId.put(contract.getContractId(), contract);
    }

    public void clear() {
        byId.clear();
    }
}
