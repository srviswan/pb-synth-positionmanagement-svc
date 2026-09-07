package com.bank.esps.domain.cdm.repository;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.basket.BasketActivity;

import java.util.Optional;

/**
 * State-saving persistence for {@link BasketActivity}.
 *
 * <p>The whole aggregate is loaded and saved. There is no event append or
 * replay; child rows (details, open lots, closing lots, settlements) are
 * the history.
 */
public interface BasketActivityRepository {

    Optional<BasketActivity> findByActivityId(String activityId);

    Optional<BasketActivity> findOpenByContract(String contractId, String underlierId, PositionDirection direction);

    Optional<BasketActivity> findLatestByContract(String contractId, String underlierId, PositionDirection direction);

    void save(BasketActivity activity);
}
