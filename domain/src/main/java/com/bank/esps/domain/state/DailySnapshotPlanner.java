package com.bank.esps.domain.state;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds one region's daily rows. Positions with activity that day are written from
 * their live book. Every other ACTIVE position is copied from the previous business date.
 */
public final class DailySnapshotPlanner {
    private DailySnapshotPlanner() {
    }

    public static SnapshotBatch plan(LocalDate businessDate,
                                     List<DailyPositionRow> previousPositions,
                                     List<DailyLotRow> previousOpenLots,
                                     Map<String, PositionBook> dirtyBooks) {
        List<DailyPositionRow> positions = new ArrayList<>();
        List<DailyLotRow> lots = new ArrayList<>();
        Set<String> dirty = dirtyBooks.keySet();

        for (PositionBook book : dirtyBooks.values()) {
            positions.add(DailyPositionRow.from(book, businessDate));
            for (ManagedLot lot : book.lotsForSnapshot(businessDate)) {
                lots.add(DailyLotRow.from(book, lot, businessDate));
            }
        }

        for (DailyPositionRow previous : previousPositions) {
            if (dirty.contains(previous.positionKey()) || previous.status() != LifecycleStatus.ACTIVE) {
                continue;
            }
            positions.add(previous.carryTo(businessDate));
        }
        for (DailyLotRow previous : previousOpenLots) {
            if (dirty.contains(previous.positionKey()) || previous.status() != LotStatus.OPEN) {
                continue;
            }
            lots.add(previous.carryTo(businessDate));
        }
        return new SnapshotBatch(List.copyOf(positions), List.copyOf(lots));
    }

    public record SnapshotBatch(List<DailyPositionRow> positions, List<DailyLotRow> lots) {
    }
}
