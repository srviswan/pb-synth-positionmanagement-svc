package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.BasketActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BasketActivityJpaRepository extends JpaRepository<BasketActivityEntity, String> {

    Optional<BasketActivityEntity> findByContractIdAndUnderlierIdAndDirectionAndPositionStatusNot(
            String contractId, String underlierId, String direction, String closedStatus);

    Optional<BasketActivityEntity> findFirstByContractIdAndUnderlierIdAndDirectionOrderByVersionDesc(
            String contractId, String underlierId, String direction);
}
