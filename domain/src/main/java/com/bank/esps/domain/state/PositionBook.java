package com.bank.esps.domain.state;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Live position. Trades and resets mutate this book in place.
 * Daily rows are derived from it; they are not replayed to recover it.
 */
public final class PositionBook {
    private static final int PRICE_SCALE = 8;

    private final String positionKey;
    private String region;
    private String account;
    private String instrument;
    private String currency;
    private LifecycleStatus status;
    private String upi;
    private long nextLotSeq;
    private LocalDate asOfDate;
    private BigDecimal realizedBaseline = BigDecimal.ZERO;
    private final List<ManagedLot> lots = new ArrayList<>();
    private final List<StateHistory> history = new ArrayList<>();
    private final List<UpiChange> upiChanges = new ArrayList<>();

    private PositionBook(String positionKey) {
        this.positionKey = positionKey;
    }

    public static PositionBook create(String positionKey) {
        return new PositionBook(positionKey);
    }

    public static PositionBook restore(String positionKey,
                                       String region,
                                       String account,
                                       String instrument,
                                       String currency,
                                       LifecycleStatus status,
                                       String upi,
                                       long nextLotSeq,
                                       LocalDate asOfDate,
                                       List<ManagedLot> lots,
                                       BigDecimal realizedBaseline) {
        PositionBook book = new PositionBook(positionKey);
        book.region = region;
        book.account = account;
        book.instrument = instrument;
        book.currency = currency;
        book.status = status;
        book.upi = upi;
        book.nextLotSeq = nextLotSeq;
        book.asOfDate = asOfDate;
        book.realizedBaseline = realizedBaseline == null ? BigDecimal.ZERO : realizedBaseline;
        for (ManagedLot lot : lots) {
            book.lots.add(lot.copy());
        }
        return book;
    }

    public PositionBook copy() {
        PositionBook copy = restore(positionKey, region, account, instrument, currency,
                status, upi, nextLotSeq, asOfDate, lots, realizedBaseline);
        copy.history.addAll(history);
        copy.upiChanges.addAll(upiChanges);
        return copy;
    }

    public void apply(StateActivity activity) {
        if (asOfDate != null && activity.effectiveDate().isBefore(asOfDate)) {
            throw new IllegalArgumentException(
                    "Activity " + activity.tradeId() + " is before the position as-of date " + asOfDate);
        }
        switch (activity.type()) {
            case OPEN -> open(activity);
            case CLOSE -> close(activity);
            case RESET -> reset(activity);
            case STOCK_SPLIT -> stockSplit(activity);
            case DIVIDEND -> dividend(activity);
        }
        asOfDate = activity.effectiveDate();
    }

    /**
     * Replay activities that were already ordered by effective date.
     * Returns an end-of-day copy after each distinct date.
     */
    public List<PositionBook> replay(List<StateActivity> activities) {
        List<PositionBook> days = new ArrayList<>();
        LocalDate current = null;
        for (StateActivity activity : activities) {
            if (current != null && !activity.effectiveDate().equals(current)) {
                days.add(copy());
            }
            apply(activity);
            current = activity.effectiveDate();
        }
        if (current != null) {
            days.add(copy());
        }
        return days;
    }

    private void open(StateActivity activity) {
        BigDecimal qtyBefore = totalQty();
        LifecycleStatus statusBefore = status;
        if (status == null || status == LifecycleStatus.TERMINATED) {
            if (activity.region() == null || activity.account() == null
                    || activity.instrument() == null || activity.currency() == null) {
                throw new IllegalArgumentException(
                        "region, account, instrument, and currency are required to open a position");
            }
            String previous = upi;
            upi = activity.tradeId();
            status = LifecycleStatus.ACTIVE;
            region = activity.region();
            account = activity.account();
            instrument = activity.instrument();
            currency = activity.currency();
            upiChanges.add(new UpiChange(
                    upi,
                    previous,
                    previous == null ? UpiChangeType.CREATED : UpiChangeType.REOPENED,
                    activity.tradeId(),
                    activity.effectiveDate()));
        }
        lots.add(ManagedLot.opened(nextLotId(), activity.tradeId(), activity.quantity(),
                activity.price(), activity.effectiveDate(), activity.settlementDate()));
        record(activity, qtyBefore, statusBefore);
    }

    private void close(StateActivity activity) {
        if (status != LifecycleStatus.ACTIVE) {
            throw new IllegalStateException("Cannot close a position that is not ACTIVE");
        }
        BigDecimal left = activity.quantity();
        if (left.compareTo(totalQty()) > 0) {
            throw new IllegalArgumentException("Close quantity exceeds open quantity");
        }
        BigDecimal qtyBefore = totalQty();
        LifecycleStatus statusBefore = status;
        List<ManagedLot> closedSlices = new ArrayList<>();
        for (ManagedLot lot : openLots()) {
            if (left.signum() == 0) {
                break;
            }
            if (left.compareTo(lot.getRemainingQty()) >= 0) {
                left = left.subtract(lot.getRemainingQty());
                lot.closeFully(activity.tradeId(), activity.price(), activity.effectiveDate());
            } else {
                closedSlices.add(lot.splitClosed(nextLotId(), activity.tradeId(), left,
                        activity.price(), activity.effectiveDate()));
                left = BigDecimal.ZERO;
            }
        }
        lots.addAll(closedSlices);
        if (totalQty().signum() == 0) {
            status = LifecycleStatus.TERMINATED;
            upiChanges.add(new UpiChange(upi, upi, UpiChangeType.TERMINATED,
                    activity.tradeId(), activity.effectiveDate()));
        }
        record(activity, qtyBefore, statusBefore);
    }

    private void reset(StateActivity activity) {
        requireActive("reset");
        BigDecimal qtyBefore = totalQty();
        for (ManagedLot lot : openLots()) {
            lot.reprice(activity.price());
        }
        record(activity, qtyBefore, status);
    }

    private void stockSplit(StateActivity activity) {
        requireActive("split");
        BigDecimal qtyBefore = totalQty();
        for (ManagedLot lot : openLots()) {
            lot.applySplit(activity.quantity());
        }
        record(activity, qtyBefore, status);
    }

    private void dividend(StateActivity activity) {
        requireActive("pay a dividend on");
        BigDecimal qtyBefore = totalQty();
        for (ManagedLot lot : openLots()) {
            lot.payDividend(activity.price());
        }
        record(activity, qtyBefore, status);
    }

    private void requireActive(String action) {
        if (status != LifecycleStatus.ACTIVE) {
            throw new IllegalStateException("Cannot " + action + " a position that is not ACTIVE");
        }
    }

    private void record(StateActivity activity, BigDecimal qtyBefore, LifecycleStatus statusBefore) {
        history.add(new StateHistory(
                activity.tradeId(),
                activity.effectiveDate(),
                qtyBefore,
                totalQty(),
                statusBefore,
                status,
                avgPrice()));
    }

    private List<ManagedLot> openLots() {
        return lots.stream().filter(lot -> lot.getStatus() == LotStatus.OPEN).toList();
    }

    private String nextLotId() {
        nextLotSeq++;
        return positionKey + "-L" + nextLotSeq;
    }

    public BigDecimal totalQty() {
        return openLots().stream()
                .map(ManagedLot::getRemainingQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal avgPrice() {
        BigDecimal qty = totalQty();
        if (qty.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = openLots().stream()
                .map(lot -> lot.getRemainingQty().multiply(lot.getOpenPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return value.divide(qty, PRICE_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal realizedPnl() {
        return realizedBaseline.add(lots.stream()
                .map(ManagedLot::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public int openLotCount() {
        return openLots().size();
    }

    public List<ManagedLot> lotsForSnapshot(LocalDate businessDate) {
        return lots.stream()
                .filter(lot -> lot.getStatus() == LotStatus.OPEN || businessDate.equals(lot.getClosedOn()))
                .map(ManagedLot::copy)
                .toList();
    }

    public String getPositionKey() { return positionKey; }
    public String getRegion() { return region; }
    public String getAccount() { return account; }
    public String getInstrument() { return instrument; }
    public String getCurrency() { return currency; }
    public LifecycleStatus getStatus() { return status; }
    public String getUpi() { return upi; }
    public long getNextLotSeq() { return nextLotSeq; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public List<ManagedLot> getLots() { return List.copyOf(lots); }
    public List<StateHistory> getHistory() { return List.copyOf(history); }
    public List<UpiChange> getUpiChanges() { return List.copyOf(upiChanges); }
}
