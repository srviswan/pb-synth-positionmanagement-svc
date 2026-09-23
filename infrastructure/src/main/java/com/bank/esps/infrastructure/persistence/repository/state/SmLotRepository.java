package com.bank.esps.infrastructure.persistence.repository.state;

import com.bank.esps.domain.state.LotStatus;
import com.bank.esps.infrastructure.persistence.entity.state.SmLotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SmLotRepository extends JpaRepository<SmLotEntity, String> {
    List<SmLotEntity> findByPositionKey(String positionKey);

    void deleteByPositionKeyAndStatus(String positionKey, LotStatus status);

    void deleteByPositionKeyAndClosedOnGreaterThanEqual(String positionKey, LocalDate closedOn);
}
