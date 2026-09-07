package com.bank.esps.infrastructure.persistence.repository;

import com.bank.esps.infrastructure.persistence.entity.BasketActivityDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BasketActivityDetailJpaRepository extends JpaRepository<BasketActivityDetailEntity, String> {
    List<BasketActivityDetailEntity> findByActivityId(String activityId);
}
