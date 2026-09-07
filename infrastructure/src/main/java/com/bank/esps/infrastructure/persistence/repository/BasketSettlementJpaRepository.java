package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.BasketSettlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BasketSettlementJpaRepository extends JpaRepository<BasketSettlementEntity, String> {
    List<BasketSettlementEntity> findByActivityId(String activityId);
}
