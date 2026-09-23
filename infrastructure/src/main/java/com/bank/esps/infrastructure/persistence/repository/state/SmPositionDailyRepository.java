package com.bank.esps.infrastructure.persistence.repository.state;

import com.bank.esps.infrastructure.persistence.entity.state.SmPositionDailyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SmPositionDailyRepository extends JpaRepository<SmPositionDailyEntity, SmPositionDailyEntity.Key> {
    List<SmPositionDailyEntity> findByRegionAndBusinessDate(String region, LocalDate businessDate);

    Optional<SmPositionDailyEntity> findFirstByPositionKeyAndBusinessDateLessThanOrderByBusinessDateDesc(
            String positionKey, LocalDate businessDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SmPositionDailyEntity e where e.region = :region and e.businessDate = :businessDate")
    void deleteForRegionDay(String region, LocalDate businessDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SmPositionDailyEntity e where e.positionKey = :positionKey and e.businessDate >= :businessDate")
    void deleteFrom(String positionKey, LocalDate businessDate);
}
