package com.bank.esps.domain.state;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyLotRow(
        String positionKey,
        String region,
        String lotId,
        LocalDate businessDate,
        BigDecimal remainingQty,
        BigDecimal tradeableQty,
        BigDecimal originalQty,
        BigDecimal openPrice,
        LotStatus status,
        LocalDate settlementDate,
        BigDecimal realizedPnl) {

    public DailyLotRow carryTo(LocalDate businessDate) {
        return new DailyLotRow(
                positionKey,
                region,
                lotId,
                businessDate,
                remainingQty,
                tradeable(remainingQty, settlementDate, businessDate),
                originalQty,
                openPrice,
                LotStatus.OPEN,
                settlementDate,
                realizedPnl);
    }

    public static DailyLotRow from(PositionBook book, ManagedLot lot, LocalDate businessDate) {
        return new DailyLotRow(
                book.getPositionKey(),
                book.getRegion(),
                lot.getLotId(),
                businessDate,
                lot.getRemainingQty(),
                tradeable(lot.getRemainingQty(), lot.getSettlementDate(), businessDate),
                lot.getOriginalQty(),
                lot.getOpenPrice(),
                lot.getStatus(),
                lot.getSettlementDate(),
                lot.getRealizedPnl());
    }

    static BigDecimal tradeable(BigDecimal remaining, LocalDate settlementDate, LocalDate businessDate) {
        if (settlementDate == null || !settlementDate.isAfter(businessDate)) {
            return remaining;
        }
        return BigDecimal.ZERO;
    }
}
