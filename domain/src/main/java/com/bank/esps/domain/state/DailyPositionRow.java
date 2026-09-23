package com.bank.esps.domain.state;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPositionRow(
        String positionKey,
        String region,
        LocalDate businessDate,
        BigDecimal totalQty,
        BigDecimal avgPrice,
        int openLotCount,
        LifecycleStatus status,
        String upi,
        BigDecimal realizedPnl) {

    public DailyPositionRow carryTo(LocalDate businessDate) {
        return new DailyPositionRow(positionKey, region, businessDate, totalQty, avgPrice,
                openLotCount, status, upi, realizedPnl);
    }

    public static DailyPositionRow from(PositionBook book, LocalDate businessDate) {
        return new DailyPositionRow(
                book.getPositionKey(),
                book.getRegion(),
                businessDate,
                book.totalQty(),
                book.avgPrice(),
                book.openLotCount(),
                book.getStatus(),
                book.getUpi(),
                book.realizedPnl());
    }
}
