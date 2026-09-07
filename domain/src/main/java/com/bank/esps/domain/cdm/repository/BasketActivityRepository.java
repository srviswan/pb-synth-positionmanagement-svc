package com.bank.esps.domain.cdm.repository;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.basket.BasketActivity;

import java.util.List;
import java.util.Optional;

/**
 * State-saving persistence for {@link BasketActivity}.
 *
 * <p>The whole aggregate is loaded and saved. There is no event append or
 * replay; child rows (details, open lots, closing lots, settlements,
 * dividend lots) are the history.
 *
 * <p>Natural key is contractId + securityId + direction.
 */
public interface BasketActivityRepository {

    Optional<BasketActivity> findByActivityId(String activityId);

    Optional<BasketActivity> findByPositionKey(String positionKey);

    Optional<BasketActivity> findOpenByContract(String contractId, String securityId, PositionDirection direction);

    Optional<BasketActivity> findLatestByContract(String contractId, String securityId, PositionDirection direction);

    List<BasketActivity> findByContractId(String contractId);

    void save(BasketActivity activity);
}
