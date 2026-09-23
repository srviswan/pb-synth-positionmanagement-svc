package com.bank.esps.infrastructure.persistence.repository.state;

import com.bank.esps.infrastructure.persistence.entity.state.SmTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SmTradeRepository extends JpaRepository<SmTradeEntity, String> {
    boolean existsByTradeId(String tradeId);

    boolean existsByPositionKeyAndEffectiveDateLessThan(String positionKey, LocalDate effectiveDate);

    List<SmTradeEntity> findByPositionKeyAndEffectiveDateGreaterThanEqualOrderByEffectiveDateAscAppliedAtAsc(
            String positionKey, LocalDate effectiveDate);

    List<SmTradeEntity> findByRegionAndEffectiveDate(String region, LocalDate effectiveDate);

    List<SmTradeEntity> findByPositionKeyOrderByEffectiveDateAscAppliedAtAsc(String positionKey);
}
