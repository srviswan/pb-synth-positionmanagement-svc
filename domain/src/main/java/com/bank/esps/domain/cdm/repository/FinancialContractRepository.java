package com.bank.esps.domain.cdm.repository;

import com.bank.esps.domain.cdm.product.FinancialContract;

import java.util.Optional;

/**
 * Persistence for legal swap/CFD contracts. Product type and underlier live here,
 * not on individual hedge trades.
 */
public interface FinancialContractRepository {

    Optional<FinancialContract> findByContractId(String contractId);

    void save(FinancialContract contract);
}
