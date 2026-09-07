package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.basket.DividendClosingLot;
import com.bank.esps.domain.cdm.basket.DividendOpenLot;
import com.bank.esps.domain.cdm.basket.OpenLot;
import com.bank.esps.domain.cdm.event.DividendInstruction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies a dividend corporate action to a Position's sibling dividend-lot tables.
 */
public final class ApplyDividendToBasket {

    private ApplyDividendToBasket() {
    }

    public static BasketActivity apply(BasketActivity activity, DividendInstruction instruction) {
        if (activity == null) {
            throw new IllegalArgumentException("BasketActivity is required");
        }
        if (instruction == null || instruction.getDividendId() == null || instruction.getDividendId().isBlank()) {
            throw new IllegalArgumentException("dividendId is required");
        }
        if (activity.getDividendOpenLots() == null) {
            activity.setDividendOpenLots(new ArrayList<>());
        }
        if (activity.getDividendClosingLots() == null) {
            activity.setDividendClosingLots(new ArrayList<>());
        }
        DividendInstruction.Action action = instruction.getAction() != null
                ? instruction.getAction()
                : DividendInstruction.Action.OPEN;
        if (action == DividendInstruction.Action.CLOSE) {
            close(activity, instruction);
        } else {
            open(activity, instruction);
        }
        activity.setVersion(activity.getVersion() + 1);
        activity.setUpdatedAt(OffsetDateTime.now());
        return activity;
    }

    private static void open(BasketActivity activity, DividendInstruction instruction) {
        List<OpenLot> sourceLots = activity.getOpenLots() == null ? List.of() : activity.getOpenLots();
        if (sourceLots.isEmpty()) {
            addOpenLot(activity, instruction, null, activity.totalQuantity().abs());
            return;
        }
        for (OpenLot lot : sourceLots) {
            addOpenLot(activity, instruction, lot.getLotId(), lot.remaining().abs());
        }
    }

    private static void addOpenLot(BasketActivity activity, DividendInstruction instruction,
                                   String sourceOpenLotId, BigDecimal quantity) {
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ZERO;
        if (qty.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal amount = instruction.getAmount() != null
                ? instruction.getAmount()
                : (instruction.getRate() != null ? qty.multiply(instruction.getRate()) : BigDecimal.ZERO);
        activity.getDividendOpenLots().add(DividendOpenLot.builder()
                .lotId(UUID.randomUUID().toString())
                .sourceOpenLotId(sourceOpenLotId)
                .dividendId(instruction.getDividendId())
                .exDate(instruction.getExDate())
                .payDate(instruction.getPayDate())
                .quantity(qty)
                .remainingQuantity(qty)
                .rate(instruction.getRate())
                .amount(amount)
                .currency(instruction.getCurrency())
                .build());
    }

    private static void close(BasketActivity activity, DividendInstruction instruction) {
        List<DividendOpenLot> remaining = new ArrayList<>();
        for (DividendOpenLot open : activity.getDividendOpenLots()) {
            if (instruction.getDividendId().equals(open.getDividendId())
                    && open.remaining().compareTo(BigDecimal.ZERO) != 0) {
                activity.getDividendClosingLots().add(DividendClosingLot.builder()
                        .closingLotId(UUID.randomUUID().toString())
                        .openedDividendLotId(open.getLotId())
                        .dividendId(open.getDividendId())
                        .closedQuantity(open.remaining())
                        .amount(open.getAmount())
                        .payDate(instruction.getPayDate() != null ? instruction.getPayDate() : open.getPayDate())
                        .currency(open.getCurrency() != null ? open.getCurrency() : instruction.getCurrency())
                        .build());
                open.setRemainingQuantity(BigDecimal.ZERO);
            }
            if (open.remaining().compareTo(BigDecimal.ZERO) != 0) {
                remaining.add(open);
            }
        }
        activity.setDividendOpenLots(remaining);
    }
}
