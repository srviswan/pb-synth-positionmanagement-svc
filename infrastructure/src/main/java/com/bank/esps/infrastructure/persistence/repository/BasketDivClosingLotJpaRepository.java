package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.BasketDivClosingLotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BasketDivClosingLotJpaRepository extends JpaRepository<BasketDivClosingLotEntity, String> {
    List<BasketDivClosingLotEntity> findByActivityId(String activityId);
}
