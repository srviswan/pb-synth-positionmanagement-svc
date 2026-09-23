package com.bank.esps.domain.state;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionBookTest {

    private static final LocalDate MAR_18 = LocalDate.of(2026, 3, 18);
    private static final LocalDate MAR_19 = LocalDate.of(2026, 3, 19);

    @Test
    void closeSplitsLotAndResetRepricesOnlyTheOpenRemainder() {
        PositionBook book = PositionBook.create("POS-EQ-1");
        book.apply(open("T-1", "100", "10"));
        book.apply(close("T-440", "50", "12"));
        book.apply(reset("R-1", "11.50"));

        assertEquals(LifecycleStatus.ACTIVE, book.getStatus());
        assertEquals("T-1", book.getUpi());
        assertEquals(0, new BigDecimal("50").compareTo(book.totalQty()));
        assertEquals(0, new BigDecimal("11.50").compareTo(book.avgPrice()));
        assertEquals(0, new BigDecimal("175").compareTo(book.realizedPnl()));

        ManagedLot open = book.getLots().stream()
                .filter(lot -> lot.getStatus() == LotStatus.OPEN)
                .findFirst()
                .orElseThrow();
        ManagedLot closed = book.getLots().stream()
                .filter(lot -> lot.getStatus() == LotStatus.CLOSED)
                .findFirst()
                .orElseThrow();

        assertEquals("POS-EQ-1-L1", open.getLotId());
        assertEquals(0, new BigDecimal("50").compareTo(open.getRemainingQty()));
        assertEquals(0, new BigDecimal("11.50").compareTo(open.getOpenPrice()));
        assertEquals(0, new BigDecimal("75").compareTo(open.getRealizedPnl()));

        assertEquals(open.getLotId(), closed.getParentLotId());
        assertEquals(0, new BigDecimal("50").compareTo(closed.getOriginalQty()));
        assertEquals(0, new BigDecimal("10.00").compareTo(closed.getOpenPrice()));
        assertEquals(0, new BigDecimal("100").compareTo(closed.getRealizedPnl()));

        assertEquals(2, book.lotsForSnapshot(MAR_18).size());
    }

    @Test
    void carryForwardCopiesResetPriceAndSkipsTheClosedSlice() {
        PositionBook book = workedExample();
        DailyLotRow openOnCloseDay = book.lotsForSnapshot(MAR_18).stream()
                .map(lot -> DailyLotRow.from(book, lot, MAR_18))
                .filter(row -> row.status() == LotStatus.OPEN)
                .findFirst()
                .orElseThrow();

        DailySnapshotPlanner.SnapshotBatch nextDay = DailySnapshotPlanner.plan(
                MAR_19,
                List.of(DailyPositionRow.from(book, MAR_18)),
                List.of(openOnCloseDay),
                Map.of());

        assertEquals(1, nextDay.lots().size());
        DailyLotRow carried = nextDay.lots().get(0);
        assertEquals("POS-EQ-1-L1", carried.lotId());
        assertEquals(MAR_19, carried.businessDate());
        assertEquals(0, new BigDecimal("50").compareTo(carried.remainingQty()));
        assertEquals(0, new BigDecimal("11.50").compareTo(carried.openPrice()));
        assertEquals(LotStatus.OPEN, carried.status());
        assertEquals(1, nextDay.positions().size());
        assertEquals("T-1", nextDay.positions().get(0).upi());
    }

    @Test
    void dirtyPositionIsNotCarriedAndThenRewritten() {
        PositionBook dirty = workedExample();
        PositionBook quiet = PositionBook.create("POS-OTHER");
        quiet.apply(activity(ActivityType.OPEN, "T-9", "20", "5", MAR_18));

        DailySnapshotPlanner.SnapshotBatch batch = DailySnapshotPlanner.plan(
                MAR_19,
                List.of(DailyPositionRow.from(quiet, MAR_18)),
                List.of(DailyLotRow.from(quiet, quiet.getLots().get(0), MAR_18)),
                Map.of(dirty.getPositionKey(), dirty));

        long copiesOfDirty = batch.lots().stream()
                .filter(row -> row.positionKey().equals("POS-EQ-1"))
                .count();
        assertEquals(1, copiesOfDirty);
        assertTrue(batch.lots().stream().anyMatch(row ->
                row.positionKey().equals("POS-OTHER") && row.businessDate().equals(MAR_19)));
    }

    @Test
    void fullCloseStopsCarryAndReopenMintsANewUpi() {
        PositionBook book = workedExample();
        book.apply(closeOn("T-441", "50", "13", MAR_19));

        assertEquals(LifecycleStatus.TERMINATED, book.getStatus());
        assertEquals("T-1", book.getUpi());
        assertEquals(0, BigDecimal.ZERO.compareTo(book.totalQty()));
        assertTrue(book.lotsForSnapshot(MAR_19).stream().allMatch(lot -> lot.getStatus() == LotStatus.CLOSED));

        DailySnapshotPlanner.SnapshotBatch noCarry = DailySnapshotPlanner.plan(
                LocalDate.of(2026, 3, 20),
                List.of(DailyPositionRow.from(book, MAR_19)),
                List.of(),
                Map.of());
        assertTrue(noCarry.positions().isEmpty());

        book.apply(openOn("T-500", "80", "14", LocalDate.of(2026, 3, 20)));
        assertEquals(LifecycleStatus.ACTIVE, book.getStatus());
        assertEquals("T-500", book.getUpi());
        assertEquals(0, new BigDecimal("80").compareTo(book.totalQty()));
        assertEquals(0, new BigDecimal("14").compareTo(book.avgPrice()));
        assertEquals(UpiChangeType.REOPENED, book.getUpiChanges().get(book.getUpiChanges().size() - 1).changeType());
    }

    @Test
    void replayFromPriorOpenLotRewritesTheLaterDay() {
        ManagedLot prior = ManagedLot.opened("POS-EQ-1-L1", "T-1", new BigDecimal("100"),
                new BigDecimal("10"), LocalDate.of(2026, 3, 17), MAR_18);
        PositionBook book = PositionBook.restore(
                "POS-EQ-1", "APAC", "A1", "AAPL", "USD",
                LifecycleStatus.ACTIVE, "T-1", 1, LocalDate.of(2026, 3, 17),
                List.of(prior), BigDecimal.ZERO);

        List<PositionBook> days = book.replay(List.of(
                close("T-440", "50", "12"),
                reset("R-1", "11.50")));

        assertEquals(1, days.size());
        assertEquals(0, new BigDecimal("11.50").compareTo(days.get(0).avgPrice()));
        assertEquals(0, new BigDecimal("50").compareTo(book.totalQty()));
    }

    @Test
    void topUpCreatesAnotherLotAndKeepsTheSameUpi() {
        PositionBook book = PositionBook.create("POS-EQ-1");
        book.apply(open("T-1", "100", "10"));
        book.apply(open("T-2", "25", "11"));

        assertEquals("T-1", book.getUpi());
        assertEquals(2, book.openLotCount());
        assertEquals(0, new BigDecimal("125").compareTo(book.totalQty()));
        assertEquals(UpiChangeType.CREATED, book.getUpiChanges().get(0).changeType());
        assertEquals(1, book.getUpiChanges().size());
    }

    @Test
    void exactCloseDoesNotSplitAndTerminatesTheUpi() {
        PositionBook book = PositionBook.create("POS-EQ-1");
        book.apply(open("T-1", "100", "10"));
        book.apply(close("T-2", "100", "12"));

        assertEquals(1, book.getLots().size());
        assertEquals(LotStatus.CLOSED, book.getLots().get(0).getStatus());
        assertEquals(LifecycleStatus.TERMINATED, book.getStatus());
        assertEquals("T-1", book.getUpi());
        assertEquals(UpiChangeType.TERMINATED, book.getUpiChanges().get(1).changeType());
    }

    @Test
    void closeDrawsFifoAcrossTwoLots() {
        PositionBook book = PositionBook.create("POS-EQ-1");
        book.apply(open("T-1", "40", "10"));
        book.apply(open("T-2", "60", "11"));
        book.apply(close("T-3", "50", "12"));

        ManagedLot first = book.getLots().stream()
                .filter(lot -> "POS-EQ-1-L1".equals(lot.getLotId()))
                .findFirst()
                .orElseThrow();
        ManagedLot second = book.getLots().stream()
                .filter(lot -> "POS-EQ-1-L2".equals(lot.getLotId()))
                .findFirst()
                .orElseThrow();
        assertEquals(LotStatus.CLOSED, first.getStatus());
        assertEquals(LotStatus.OPEN, second.getStatus());
        assertEquals(0, new BigDecimal("50").compareTo(second.getRemainingQty()));
        assertEquals(0, new BigDecimal("50").compareTo(book.totalQty()));
    }

    @Test
    void settlementQuantityBecomesTradeableOnTheSettlementDate() {
        LocalDate tradeDate = MAR_18;
        LocalDate settlement = LocalDate.of(2026, 3, 20);
        PositionBook book = PositionBook.create("POS-EQ-1");
        book.apply(new StateActivity("T-1", ActivityType.OPEN, new BigDecimal("100"), new BigDecimal("10"),
                tradeDate, settlement, "APAC", "A1", "AAPL", "USD"));
        DailyLotRow opened = DailyLotRow.from(book, book.getLots().get(0), tradeDate);
        assertEquals(0, BigDecimal.ZERO.compareTo(opened.tradeableQty()));

        DailyLotRow dayBefore = opened.carryTo(LocalDate.of(2026, 3, 19));
        DailyLotRow settled = dayBefore.carryTo(settlement);
        assertEquals(0, BigDecimal.ZERO.compareTo(dayBefore.tradeableQty()));
        assertEquals(0, new BigDecimal("100").compareTo(settled.tradeableQty()));
    }

    @Test
    void plannerDoesNotInventPositionsFromAnotherRegion() {
        PositionBook apac = PositionBook.create("POS-APAC");
        apac.apply(open("T-1", "10", "10"));
        DailySnapshotPlanner.SnapshotBatch batch = DailySnapshotPlanner.plan(
                MAR_19,
                List.of(DailyPositionRow.from(apac, MAR_18)),
                List.of(DailyLotRow.from(apac, apac.getLots().get(0), MAR_18)),
                Map.of());

        assertEquals(1, batch.positions().size());
        assertEquals("APAC", batch.positions().get(0).region());
        assertTrue(batch.lots().stream().allMatch(row -> row.positionKey().equals("POS-APAC")));
    }

    @Test
    void illegalClosesAndResetsAreRejected() {
        PositionBook book = PositionBook.create("POS-EQ-1");
        book.apply(open("T-1", "100", "10"));
        assertThrows(IllegalArgumentException.class, () -> book.apply(close("T-2", "150", "12")));
        book.apply(close("T-3", "100", "12"));
        assertThrows(IllegalStateException.class, () -> book.apply(reset("R-1", "11")));
        assertThrows(IllegalStateException.class, () -> book.apply(close("T-4", "1", "12")));
        assertThrows(IllegalArgumentException.class,
                () -> book.apply(openOn("T-5", "10", "9", LocalDate.of(2026, 3, 17))));
    }

    @Test
    void historyRecordsTheQuantityChange() {
        PositionBook book = PositionBook.create("POS-EQ-1");
        book.apply(open("T-1", "100", "10"));
        book.apply(close("T-440", "50", "12"));

        StateHistory close = book.getHistory().get(1);
        assertEquals(0, new BigDecimal("100").compareTo(close.qtyBefore()));
        assertEquals(0, new BigDecimal("50").compareTo(close.qtyAfter()));
        assertEquals(LifecycleStatus.ACTIVE, close.statusAfter());
    }

    @Test
    void resetRepricesEveryOpenLotAndLeavesClosedLotsAlone() {
        PositionBook book = PositionBook.create("POS-EQ-1");
        book.apply(open("T-1", "100", "10"));
        book.apply(open("T-2", "50", "20"));
        book.apply(close("T-3", "20", "12"));
        book.apply(reset("R-1", "12"));

        ManagedLot closed = book.getLots().stream()
                .filter(lot -> lot.getStatus() == LotStatus.CLOSED)
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("10").compareTo(closed.getOpenPrice()));
        assertEquals(0, new BigDecimal("40").compareTo(closed.getRealizedPnl()));

        assertTrue(book.getLots().stream()
                .filter(lot -> lot.getStatus() == LotStatus.OPEN)
                .allMatch(lot -> lot.getOpenPrice().compareTo(new BigDecimal("12")) == 0));
        assertEquals("T-1", book.getUpi());
        assertEquals(0, new BigDecimal("130").compareTo(book.totalQty()));
        assertEquals(0, new BigDecimal("12").compareTo(book.avgPrice()));
        // first lot remainder 80: (12-10)*80 = 160; second lot 50: (12-20)*50 = -400; close pnl 40
        assertEquals(0, new BigDecimal("-200").compareTo(book.realizedPnl()));
    }

    @Test
    void stockSplitScalesOpenLotsAndDividendThenResetUsesTheNewPrice() {
        PositionBook book = PositionBook.create("POS-EQ-1");
        book.apply(open("T-1", "100", "10"));
        book.apply(close("T-2", "40", "12"));
        book.apply(split("S-1", "2"));

        ManagedLot open = openLot(book);
        ManagedLot closed = book.getLots().stream()
                .filter(lot -> lot.getStatus() == LotStatus.CLOSED)
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("120").compareTo(open.getRemainingQty()));
        assertEquals(0, new BigDecimal("5").compareTo(open.getOpenPrice()));
        assertEquals(0, new BigDecimal("40").compareTo(closed.getOriginalQty()));
        assertEquals(0, new BigDecimal("10").compareTo(closed.getOpenPrice()));
        assertEquals("T-1", book.getUpi());
        assertEquals(1, book.openLotCount());

        book.apply(dividend("D-1", "0.50"));
        open = openLot(book);
        assertEquals(0, new BigDecimal("120").compareTo(open.getRemainingQty()));
        assertEquals(0, new BigDecimal("4.50").compareTo(open.getOpenPrice()));
        assertEquals(0, new BigDecimal("60").compareTo(open.getRealizedPnl()));

        book.apply(reset("R-1", "6"));
        open = openLot(book);
        assertEquals(0, new BigDecimal("6").compareTo(open.getOpenPrice()));
        assertEquals(0, new BigDecimal("120").compareTo(book.totalQty()));
        assertEquals(0, new BigDecimal("240").compareTo(open.getRealizedPnl()));

        DailyLotRow carried = DailyLotRow.from(book, open, MAR_18).carryTo(MAR_19);
        assertEquals(0, new BigDecimal("120").compareTo(carried.remainingQty()));
        assertEquals(0, new BigDecimal("6").compareTo(carried.openPrice()));
    }

    @Test
    void reverseSplitAndOversizedDividendAreHandled() {
        PositionBook book = PositionBook.create("POS-EQ-1");
        book.apply(open("T-1", "100", "10"));
        book.apply(split("S-1", "0.5"));
        ManagedLot open = openLot(book);
        assertEquals(0, new BigDecimal("50").compareTo(open.getRemainingQty()));
        assertEquals(0, new BigDecimal("20").compareTo(open.getOpenPrice()));
        assertThrows(IllegalArgumentException.class, () -> book.apply(dividend("D-1", "25")));
        book.apply(close("T-2", "50", "20"));
        assertThrows(IllegalStateException.class, () -> book.apply(split("S-2", "2")));
    }

    private static ManagedLot openLot(PositionBook book) {
        return book.getLots().stream()
                .filter(lot -> lot.getStatus() == LotStatus.OPEN)
                .findFirst()
                .orElseThrow();
    }

    private static PositionBook workedExample() {
        PositionBook book = PositionBook.create("POS-EQ-1");
        book.apply(open("T-1", "100", "10"));
        book.apply(close("T-440", "50", "12"));
        book.apply(reset("R-1", "11.50"));
        return book;
    }

    private static StateActivity open(String id, String qty, String price) {
        return activity(ActivityType.OPEN, id, qty, price, MAR_18);
    }

    private static StateActivity openOn(String id, String qty, String price, LocalDate date) {
        return activity(ActivityType.OPEN, id, qty, price, date);
    }

    private static StateActivity close(String id, String qty, String price) {
        return activity(ActivityType.CLOSE, id, qty, price, MAR_18);
    }

    private static StateActivity closeOn(String id, String qty, String price, LocalDate date) {
        return activity(ActivityType.CLOSE, id, qty, price, date);
    }

    private static StateActivity split(String id, String ratio) {
        return new StateActivity(id, ActivityType.STOCK_SPLIT, new BigDecimal(ratio), null,
                MAR_18, null, "APAC", "A1", "AAPL", "USD");
    }

    private static StateActivity dividend(String id, String amount) {
        return activity(ActivityType.DIVIDEND, id, null, amount, MAR_18);
    }

    private static StateActivity reset(String id, String price) {
        return activity(ActivityType.RESET, id, null, price, MAR_18);
    }

    private static StateActivity activity(ActivityType type, String id, String qty, String price, LocalDate date) {
        return new StateActivity(id, type, qty == null ? null : new BigDecimal(qty), new BigDecimal(price),
                date, date.plusDays(2), "APAC", "A1", "AAPL", "USD");
    }
}
