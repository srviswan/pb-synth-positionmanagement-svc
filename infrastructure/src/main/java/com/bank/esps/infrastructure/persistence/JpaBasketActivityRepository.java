package com.bank.esps.infrastructure.persistence;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.position.PositionStatus;
import com.bank.esps.domain.cdm.repository.BasketActivityRepository;
import com.bank.esps.infrastructure.persistence.entity.BasketActivityEntity;
import com.bank.esps.infrastructure.persistence.mapping.BasketActivityPersistenceMapper;
import com.bank.esps.infrastructure.persistence.repository.BasketActivityDetailJpaRepository;
import com.bank.esps.infrastructure.persistence.repository.BasketActivityJpaRepository;
import com.bank.esps.infrastructure.persistence.repository.BasketClosingLotJpaRepository;
import com.bank.esps.infrastructure.persistence.repository.BasketOpenLotJpaRepository;
import com.bank.esps.infrastructure.persistence.repository.BasketSettlementJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class JpaBasketActivityRepository implements BasketActivityRepository {

    private final BasketActivityJpaRepository activityRepository;
    private final BasketActivityDetailJpaRepository detailRepository;
    private final BasketOpenLotJpaRepository openLotRepository;
    private final BasketClosingLotJpaRepository closingLotRepository;
    private final BasketSettlementJpaRepository settlementRepository;
    private final BasketActivityPersistenceMapper mapper;

    public JpaBasketActivityRepository(BasketActivityJpaRepository activityRepository,
                                       BasketActivityDetailJpaRepository detailRepository,
                                       BasketOpenLotJpaRepository openLotRepository,
                                       BasketClosingLotJpaRepository closingLotRepository,
                                       BasketSettlementJpaRepository settlementRepository,
                                       BasketActivityPersistenceMapper mapper) {
        this.activityRepository = activityRepository;
        this.detailRepository = detailRepository;
        this.openLotRepository = openLotRepository;
        this.closingLotRepository = closingLotRepository;
        this.settlementRepository = settlementRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BasketActivity> findByActivityId(String activityId) {
        return activityRepository.findById(activityId).map(this::hydrate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BasketActivity> findOpenByContract(String contractId, String underlierId,
                                                       PositionDirection direction) {
        return activityRepository.findByContractIdAndUnderlierIdAndDirectionAndPositionStatusNot(
                        contractId, underlierId, direction.name(), PositionStatus.CLOSED.name())
                .map(this::hydrate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BasketActivity> findLatestByContract(String contractId, String underlierId,
                                                         PositionDirection direction) {
        return activityRepository.findFirstByContractIdAndUnderlierIdAndDirectionOrderByVersionDesc(
                        contractId, underlierId, direction.name())
                .map(this::hydrate);
    }

    @Override
    @Transactional
    public void save(BasketActivity activity) {
        activityRepository.save(mapper.toEntity(activity));
        activity.getDetails().forEach(detail ->
                detailRepository.save(mapper.toDetailEntity(activity.getActivityId(), detail)));
        openLotRepository.deleteByActivityId(activity.getActivityId());
        openLotRepository.flush();
        activity.getOpenLots().forEach(lot ->
                openLotRepository.save(mapper.toOpenLotEntity(activity.getActivityId(), lot)));
        activity.getClosingLots().forEach(lot ->
                closingLotRepository.save(mapper.toClosingLotEntity(activity.getActivityId(), lot)));
        activity.getSettlements().forEach(settlement ->
                settlementRepository.save(mapper.toSettlementEntity(activity.getActivityId(), settlement)));
    }

    private BasketActivity hydrate(BasketActivityEntity entity) {
        return mapper.toDomain(
                entity,
                detailRepository.findByActivityId(entity.getActivityId()),
                openLotRepository.findByActivityId(entity.getActivityId()),
                closingLotRepository.findByActivityId(entity.getActivityId()),
                settlementRepository.findByActivityId(entity.getActivityId()));
    }
}
