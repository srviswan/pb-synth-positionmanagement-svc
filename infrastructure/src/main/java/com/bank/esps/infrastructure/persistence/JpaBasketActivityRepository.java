package com.bank.esps.infrastructure.persistence;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.position.PositionKey;
import com.bank.esps.domain.cdm.position.PositionStatus;
import com.bank.esps.domain.cdm.repository.BasketActivityRepository;
import com.bank.esps.infrastructure.persistence.entity.BasketActivityEntity;
import com.bank.esps.infrastructure.persistence.mapping.BasketActivityPersistenceMapper;
import com.bank.esps.infrastructure.persistence.repository.BasketActivityDetailJpaRepository;
import com.bank.esps.infrastructure.persistence.repository.BasketActivityJpaRepository;
import com.bank.esps.infrastructure.persistence.repository.BasketClosingLotJpaRepository;
import com.bank.esps.infrastructure.persistence.repository.BasketDivClosingLotJpaRepository;
import com.bank.esps.infrastructure.persistence.repository.BasketDivOpenLotJpaRepository;
import com.bank.esps.infrastructure.persistence.repository.BasketOpenLotJpaRepository;
import com.bank.esps.infrastructure.persistence.repository.BasketSettlementJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaBasketActivityRepository implements BasketActivityRepository {

    private final BasketActivityJpaRepository activityRepository;
    private final BasketActivityDetailJpaRepository detailRepository;
    private final BasketOpenLotJpaRepository openLotRepository;
    private final BasketClosingLotJpaRepository closingLotRepository;
    private final BasketSettlementJpaRepository settlementRepository;
    private final BasketDivOpenLotJpaRepository dividendOpenLotRepository;
    private final BasketDivClosingLotJpaRepository dividendClosingLotRepository;
    private final BasketActivityPersistenceMapper mapper;

    public JpaBasketActivityRepository(BasketActivityJpaRepository activityRepository,
                                       BasketActivityDetailJpaRepository detailRepository,
                                       BasketOpenLotJpaRepository openLotRepository,
                                       BasketClosingLotJpaRepository closingLotRepository,
                                       BasketSettlementJpaRepository settlementRepository,
                                       BasketDivOpenLotJpaRepository dividendOpenLotRepository,
                                       BasketDivClosingLotJpaRepository dividendClosingLotRepository,
                                       BasketActivityPersistenceMapper mapper) {
        this.activityRepository = activityRepository;
        this.detailRepository = detailRepository;
        this.openLotRepository = openLotRepository;
        this.closingLotRepository = closingLotRepository;
        this.settlementRepository = settlementRepository;
        this.dividendOpenLotRepository = dividendOpenLotRepository;
        this.dividendClosingLotRepository = dividendClosingLotRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BasketActivity> findByActivityId(String activityId) {
        return activityRepository.findById(activityId).map(this::hydrate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BasketActivity> findByPositionKey(String positionKey) {
        return activityRepository.findFirstByPositionKeyOrderByVersionDesc(positionKey).map(this::hydrate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BasketActivity> findOpenByContract(String contractId, String securityId,
                                                       PositionDirection direction) {
        String normalized = PositionKey.normalize(securityId);
        return activityRepository.findByContractIdAndSecurityIdAndDirectionAndPositionStatusNot(
                        contractId, normalized, direction.name(), PositionStatus.CLOSED.name())
                .or(() -> activityRepository.findByContractIdAndUnderlierIdAndDirectionAndPositionStatusNot(
                        contractId, securityId, direction.name(), PositionStatus.CLOSED.name()))
                .map(this::hydrate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BasketActivity> findLatestByContract(String contractId, String securityId,
                                                         PositionDirection direction) {
        String normalized = PositionKey.normalize(securityId);
        return activityRepository.findFirstByContractIdAndSecurityIdAndDirectionOrderByVersionDesc(
                        contractId, normalized, direction.name())
                .or(() -> activityRepository.findFirstByContractIdAndUnderlierIdAndDirectionOrderByVersionDesc(
                        contractId, securityId, direction.name()))
                .map(this::hydrate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BasketActivity> findByContractId(String contractId) {
        return activityRepository.findByContractId(contractId).stream()
                .map(this::hydrate)
                .toList();
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
        dividendOpenLotRepository.deleteByActivityId(activity.getActivityId());
        dividendOpenLotRepository.flush();
        if (activity.getDividendOpenLots() != null) {
            activity.getDividendOpenLots().forEach(lot ->
                    dividendOpenLotRepository.save(mapper.toDividendOpenLotEntity(activity.getActivityId(), lot)));
        }
        if (activity.getDividendClosingLots() != null) {
            activity.getDividendClosingLots().forEach(lot ->
                    dividendClosingLotRepository.save(mapper.toDividendClosingLotEntity(activity.getActivityId(), lot)));
        }
    }

    private BasketActivity hydrate(BasketActivityEntity entity) {
        return mapper.toDomain(
                entity,
                detailRepository.findByActivityId(entity.getActivityId()),
                openLotRepository.findByActivityId(entity.getActivityId()),
                closingLotRepository.findByActivityId(entity.getActivityId()),
                settlementRepository.findByActivityId(entity.getActivityId()),
                dividendOpenLotRepository.findByActivityId(entity.getActivityId()),
                dividendClosingLotRepository.findByActivityId(entity.getActivityId()));
    }
}
