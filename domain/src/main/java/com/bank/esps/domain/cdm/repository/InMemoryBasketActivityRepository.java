package com.bank.esps.domain.cdm.repository;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.basket.BasketActivity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process state store used by domain tests and local runs.
 */
public class InMemoryBasketActivityRepository implements BasketActivityRepository {

    private final Map<String, BasketActivity> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<BasketActivity> findByActivityId(String activityId) {
        return Optional.ofNullable(byId.get(activityId));
    }

    @Override
    public Optional<BasketActivity> findOpenByContract(String contractId, String underlierId,
                                                       PositionDirection direction) {
        return matching(contractId, underlierId, direction)
                .filter(activity -> !activity.isClosed())
                .findFirst();
    }

    @Override
    public Optional<BasketActivity> findLatestByContract(String contractId, String underlierId,
                                                         PositionDirection direction) {
        return matching(contractId, underlierId, direction)
                .max(java.util.Comparator.comparingInt(BasketActivity::getVersion));
    }

    private java.util.stream.Stream<BasketActivity> matching(String contractId, String underlierId,
                                                             PositionDirection direction) {
        return byId.values().stream()
                .filter(activity -> contractId.equals(activity.getContractId()))
                .filter(activity -> underlierId == null
                        || underlierId.equals(activity.getProduct() != null ? activity.getProduct().underlierId() : null))
                .filter(activity -> direction == activity.getDirection());
    }

    @Override
    public void save(BasketActivity activity) {
        if (activity.getActivityId() == null || activity.getActivityId().isBlank()) {
            throw new IllegalArgumentException("BasketActivity.activityId is required to save state");
        }
        byId.put(activity.getActivityId(), activity);
    }

    public void clear() {
        byId.clear();
    }

    public int size() {
        return byId.size();
    }
}
