package com.bank.esps.domain.state;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * One inventory slice. A partial close leaves this lot open and adds a closed slice.
 * A reset changes {@code openPrice} and realises the difference on the open quantity.
 */
public final class ManagedLot {
    private final String lotId;
    private final String parentLotId;
    private final String openingTradeId;
    private String closingTradeId;
    private BigDecimal originalQty;
    private BigDecimal remainingQty;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal realizedPnl;
    private LotStatus status;
    private final LocalDate effectiveDate;
    private final LocalDate settlementDate;
    private LocalDate closedOn;

    public ManagedLot(String lotId,
                      String parentLotId,
                      String openingTradeId,
                      BigDecimal originalQty,
                      BigDecimal remainingQty,
                      BigDecimal openPrice,
                      BigDecimal realizedPnl,
                      LotStatus status,
                      LocalDate effectiveDate,
                      LocalDate settlementDate,
                      LocalDate closedOn,
                      String closingTradeId,
                      BigDecimal closePrice) {
        this.lotId = lotId;
        this.parentLotId = parentLotId;
        this.openingTradeId = openingTradeId;
        this.originalQty = originalQty;
        this.remainingQty = remainingQty;
        this.openPrice = openPrice;
        this.realizedPnl = realizedPnl == null ? BigDecimal.ZERO : realizedPnl;
        this.status = status;
        this.effectiveDate = effectiveDate;
        this.settlementDate = settlementDate;
        this.closedOn = closedOn;
        this.closingTradeId = closingTradeId;
        this.closePrice = closePrice;
    }

    public static ManagedLot opened(String lotId, String openingTradeId, BigDecimal quantity,
                                    BigDecimal openPrice, LocalDate effectiveDate, LocalDate settlementDate) {
        return new ManagedLot(lotId, null, openingTradeId, quantity, quantity, openPrice,
                BigDecimal.ZERO, LotStatus.OPEN, effectiveDate, settlementDate, null, null, null);
    }

    public ManagedLot copy() {
        return new ManagedLot(lotId, parentLotId, openingTradeId, originalQty, remainingQty, openPrice,
                realizedPnl, status, effectiveDate, settlementDate, closedOn, closingTradeId, closePrice);
    }

    public String getLotId() { return lotId; }
    public String getParentLotId() { return parentLotId; }
    public String getOpeningTradeId() { return openingTradeId; }
    public String getClosingTradeId() { return closingTradeId; }
    public BigDecimal getOriginalQty() { return originalQty; }
    public BigDecimal getRemainingQty() { return remainingQty; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public LotStatus getStatus() { return status; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public LocalDate getSettlementDate() { return settlementDate; }
    public LocalDate getClosedOn() { return closedOn; }

    void closeFully(String tradeId, BigDecimal closePrice, LocalDate closedOn) {
        this.realizedPnl = this.realizedPnl.add(pnl(this.openPrice, closePrice, this.remainingQty));
        this.remainingQty = BigDecimal.ZERO;
        this.status = LotStatus.CLOSED;
        this.closePrice = closePrice;
        this.closingTradeId = tradeId;
        this.closedOn = closedOn;
    }

    /**
     * Cut {@code quantity} off this open lot into a new closed lot.
     * This lot stays open for the remainder and keeps its id so the next day can carry it.
     */
    ManagedLot splitClosed(String closedLotId, String tradeId, BigDecimal quantity,
                           BigDecimal closePrice, LocalDate closedOn) {
        ManagedLot closed = new ManagedLot(
                closedLotId,
                this.lotId,
                this.openingTradeId,
                quantity,
                BigDecimal.ZERO,
                this.openPrice,
                pnl(this.openPrice, closePrice, quantity),
                LotStatus.CLOSED,
                this.effectiveDate,
                this.settlementDate,
                closedOn,
                tradeId,
                closePrice);
        this.remainingQty = this.remainingQty.subtract(quantity);
        this.originalQty = this.remainingQty;
        return closed;
    }

    void reprice(BigDecimal resetPrice) {
        this.realizedPnl = this.realizedPnl.add(pnl(this.openPrice, resetPrice, this.remainingQty));
        this.openPrice = resetPrice;
    }

    void applySplit(BigDecimal ratio) {
        this.originalQty = this.originalQty.multiply(ratio);
        this.remainingQty = this.remainingQty.multiply(ratio);
        this.openPrice = this.openPrice.divide(ratio, 8, RoundingMode.HALF_UP);
    }

    void payDividend(BigDecimal perShare) {
        if (this.openPrice.compareTo(perShare) < 0) {
            throw new IllegalArgumentException("Dividend exceeds open price");
        }
        this.realizedPnl = this.realizedPnl.add(perShare.multiply(this.remainingQty));
        this.openPrice = this.openPrice.subtract(perShare);
    }

    static BigDecimal pnl(BigDecimal openPrice, BigDecimal exitPrice, BigDecimal quantity) {
        return exitPrice.subtract(openPrice).multiply(quantity);
    }
}
