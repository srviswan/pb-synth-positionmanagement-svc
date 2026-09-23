package com.bank.esps.infrastructure.persistence.repository.state;

import com.bank.esps.domain.state.LotStatus;
import com.bank.esps.infrastructure.persistence.entity.state.SmLotDailyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface SmLotDailyRepository extends JpaRepository<SmLotDailyEntity, SmLotDailyEntity.Key> {
    List<SmLotDailyEntity> findByRegionAndBusinessDateAndStatus(
            String region, LocalDate businessDate, LotStatus status);

    List<SmLotDailyEntity> findByPositionKeyAndBusinessDate(String positionKey, LocalDate businessDate);

    List<SmLotDailyEntity> findByPositionKeyAndBusinessDateAndStatus(
            String positionKey, LocalDate businessDate, LotStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SmLotDailyEntity e where e.region = :region and e.businessDate = :businessDate")
    void deleteForRegionDay(String region, LocalDate businessDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SmLotDailyEntity e where e.positionKey = :positionKey and e.businessDate >= :businessDate")
    void deleteFrom(String positionKey, LocalDate businessDate);
}
