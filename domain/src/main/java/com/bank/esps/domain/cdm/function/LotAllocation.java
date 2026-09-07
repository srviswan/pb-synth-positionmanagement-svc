package com.bank.esps.domain.cdm.function;

import com.bank.esps.domain.cdm.position.TradeLot;
import com.bank.esps.domain.enums.TaxLotMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Allocates a decrease across open lots using FIFO, LIFO, or HIFO.
 */
public final class LotAllocation {

    private LotAllocation() {
    }

    public static List<TradeLot.LotReduction> reduce(List<TradeLot> lots, BigDecimal quantity,
                                                     TaxLotMethod method, BigDecimal closePrice,
                                                     boolean shortPosition) {
        List<TradeLot.LotReduction> reductions = new ArrayList<>();
        BigDecimal remaining = quantity.abs();
        for (TradeLot lot : sort(lots, method)) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (!lot.isOpen()) {
                continue;
            }
            TradeLot.LotReduction reduction = lot.reduce(remaining, closePrice, shortPosition);
            reductions.add(reduction);
            remaining = remaining.subtract(reduction.reducedQuantity());
        }
        if (lots != null) {
            lots.removeIf(lot -> !lot.isOpen());
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Could not fully allocate decrease of " + quantity
                    + "; remaining unallocated " + remaining);
        }
        return reductions;
    }

    static List<TradeLot> sort(List<TradeLot> lots, TaxLotMethod method) {
        List<TradeLot> sorted = new ArrayList<>(lots);
        TaxLotMethod resolved = method != null ? method : TaxLotMethod.FIFO;
        switch (resolved) {
            case LIFO -> sorted.sort(Comparator
                    .comparing((TradeLot lot) -> lot.getTradeDate() != null ? lot.getTradeDate() : LocalDate.MIN,
                            Comparator.reverseOrder())
                    .thenComparing(TradeLot::getAcquisitionSequence, Comparator.reverseOrder())
                    .thenComparing(TradeLot::lotId, Comparator.nullsLast(Comparator.reverseOrder())));
            case HIFO -> sorted.sort(Comparator
                    .comparing((TradeLot lot) -> lot.getCostBasis() != null ? lot.getCostBasis() : BigDecimal.ZERO,
                            Comparator.reverseOrder())
                    .thenComparing((TradeLot lot) -> lot.getTradeDate() != null ? lot.getTradeDate() : LocalDate.MIN,
                            Comparator.reverseOrder())
                    .thenComparing(TradeLot::getAcquisitionSequence, Comparator.reverseOrder())
                    .thenComparing(TradeLot::lotId, Comparator.nullsLast(Comparator.reverseOrder())));
            default -> sorted.sort(Comparator
                    .comparing((TradeLot lot) -> lot.getTradeDate() != null ? lot.getTradeDate() : LocalDate.MAX)
                    .thenComparing(TradeLot::getAcquisitionSequence)
                    .thenComparing(TradeLot::lotId, Comparator.nullsLast(String::compareTo)));
        }
        return sorted;
    }
}
