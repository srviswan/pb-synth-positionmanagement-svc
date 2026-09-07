package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.BasketDivOpenLotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BasketDivOpenLotJpaRepository extends JpaRepository<BasketDivOpenLotEntity, String> {
    List<BasketDivOpenLotEntity> findByActivityId(String activityId);

    void deleteByActivityId(String activityId);
}
