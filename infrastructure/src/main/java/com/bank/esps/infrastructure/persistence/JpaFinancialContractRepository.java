package com.bank.esps.infrastructure.persistence;

import com.bank.esps.domain.cdm.product.FinancialContract;
import com.bank.esps.domain.cdm.repository.FinancialContractRepository;
import com.bank.esps.infrastructure.persistence.mapping.FinancialContractPersistenceMapper;
import com.bank.esps.infrastructure.persistence.repository.FinancialContractJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class JpaFinancialContractRepository implements FinancialContractRepository {

    private final FinancialContractJpaRepository jpaRepository;
    private final FinancialContractPersistenceMapper mapper;

    public JpaFinancialContractRepository(FinancialContractJpaRepository jpaRepository,
                                          FinancialContractPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FinancialContract> findByContractId(String contractId) {
        return jpaRepository.findById(contractId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void save(FinancialContract contract) {
        jpaRepository.save(mapper.toEntity(contract));
    }
}
