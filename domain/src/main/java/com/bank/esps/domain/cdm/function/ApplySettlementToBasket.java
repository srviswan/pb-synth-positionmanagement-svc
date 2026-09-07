package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.basket.BasketSettlement;
import com.bank.esps.domain.cdm.event.SettlementInstruction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Updates settlement status/quantity on a Position's sibling settlement rows.
 */
public final class ApplySettlementToBasket {

    private ApplySettlementToBasket() {
    }

    public static BasketActivity apply(BasketActivity activity, SettlementInstruction instruction) {
        if (activity == null) {
            throw new IllegalArgumentException("BasketActivity is required");
        }
        if (instruction == null) {
            throw new IllegalArgumentException("SettlementInstruction is required");
        }
        if (activity.getSettlements() == null) {
            activity.setSettlements(new java.util.ArrayList<>());
        }
        BasketSettlement match = activity.getSettlements().stream()
                .filter(existing -> matches(existing, instruction))
                .findFirst()
                .orElse(null);
        if (match == null) {
            match = BasketSettlement.builder()
                    .settlementId(UUID.randomUUID().toString())
                    .detailId(instruction.getDetailId())
                    .tradeId(instruction.getTradeId())
                    .build();
            activity.getSettlements().add(match);
        }
        if (instruction.getSettlementDate() != null) {
            match.setSettlementDate(instruction.getSettlementDate());
        }
        if (instruction.getSettledQuantity() != null) {
            match.setSettledQuantity(instruction.getSettledQuantity());
        } else if (match.getSettledQuantity() == null) {
            match.setSettledQuantity(BigDecimal.ZERO);
        }
        if (instruction.getCurrency() != null) {
            match.setCurrency(instruction.getCurrency());
        }
        match.setStatus(instruction.getStatus() != null ? instruction.getStatus() : "SETTLED");
        activity.setVersion(activity.getVersion() + 1);
        activity.setUpdatedAt(OffsetDateTime.now());
        return activity;
    }

    private static boolean matches(BasketSettlement existing, SettlementInstruction instruction) {
        if (instruction.getDetailId() != null && instruction.getDetailId().equals(existing.getDetailId())) {
            return true;
        }
        return instruction.getTradeId() != null && instruction.getTradeId().equals(existing.getTradeId());
    }
}
