package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.BasketActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BasketActivityJpaRepository extends JpaRepository<BasketActivityEntity, String> {

    Optional<BasketActivityEntity> findByContractIdAndSecurityIdAndDirectionAndPositionStatusNot(
            String contractId, String securityId, String direction, String closedStatus);

    Optional<BasketActivityEntity> findFirstByContractIdAndSecurityIdAndDirectionOrderByVersionDesc(
            String contractId, String securityId, String direction);

    Optional<BasketActivityEntity> findFirstByPositionKeyOrderByVersionDesc(String positionKey);

    List<BasketActivityEntity> findByContractId(String contractId);

    Optional<BasketActivityEntity> findByContractIdAndUnderlierIdAndDirectionAndPositionStatusNot(
            String contractId, String underlierId, String direction, String closedStatus);

    Optional<BasketActivityEntity> findFirstByContractIdAndUnderlierIdAndDirectionOrderByVersionDesc(
            String contractId, String underlierId, String direction);
}
