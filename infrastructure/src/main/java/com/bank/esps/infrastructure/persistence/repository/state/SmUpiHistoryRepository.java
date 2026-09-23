package com.bank.esps.infrastructure.persistence.repository.state;

import com.bank.esps.infrastructure.persistence.entity.state.SmUpiHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;

public interface SmUpiHistoryRepository extends JpaRepository<SmUpiHistoryEntity, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SmUpiHistoryEntity e where e.positionKey = :positionKey and e.effectiveDate >= :effectiveDate")
    void deleteFrom(String positionKey, LocalDate effectiveDate);
}
