package com.bank.esps.infrastructure.persistence.repository.state;

import com.bank.esps.infrastructure.persistence.entity.state.SmPositionHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;

public interface SmPositionHistoryRepository extends JpaRepository<SmPositionHistoryEntity, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SmPositionHistoryEntity e where e.positionKey = :positionKey and e.effectiveDate >= :effectiveDate")
    void deleteFrom(String positionKey, LocalDate effectiveDate);
}
