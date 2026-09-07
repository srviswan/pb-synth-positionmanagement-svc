package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.BasketClosingLotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BasketClosingLotJpaRepository extends JpaRepository<BasketClosingLotEntity, String> {
    List<BasketClosingLotEntity> findByActivityId(String activityId);
}
