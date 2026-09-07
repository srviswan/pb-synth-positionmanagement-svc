package com.bank.esps.domain.cdm.repository;

import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.position.PositionKey;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

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
    public Optional<BasketActivity> findByPositionKey(String positionKey) {
        return byId.values().stream()
                .filter(activity -> positionKey != null && positionKey.equals(activity.getPositionKey()))
                .max(Comparator.comparingInt(BasketActivity::getVersion));
    }

    @Override
    public Optional<BasketActivity> findOpenByContract(String contractId, String securityId,
                                                       PositionDirection direction) {
        return matching(contractId, securityId, direction)
                .filter(activity -> !activity.isClosed())
                .findFirst();
    }

    @Override
    public Optional<BasketActivity> findLatestByContract(String contractId, String securityId,
                                                         PositionDirection direction) {
        return matching(contractId, securityId, direction)
                .max(Comparator.comparingInt(BasketActivity::getVersion));
    }

    @Override
    public List<BasketActivity> findByContractId(String contractId) {
        return byId.values().stream()
                .filter(activity -> contractId != null && contractId.equals(activity.getContractId()))
                .toList();
    }

    private Stream<BasketActivity> matching(String contractId, String securityId, PositionDirection direction) {
        String normalizedSecurity = PositionKey.normalize(securityId);
        return byId.values().stream()
                .filter(activity -> contractId != null && contractId.equals(activity.getContractId()))
                .filter(activity -> normalizedSecurity.equals(PositionKey.normalize(activity.resolvedSecurityId())))
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
