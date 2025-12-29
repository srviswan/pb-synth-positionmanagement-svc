package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Compressed representation of tax lots using parallel arrays
 * Reduces JSON size by 40-60% compared to array of objects
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompressedLots {
    
    // Parallel arrays for tax lot data
    private List<String> lotIds;
    private List<BigDecimal> originalQtys;
    private List<BigDecimal> remainingQtys;
    private List<BigDecimal> costBases;
    private List<BigDecimal> currentRefPrices;
    private List<LocalDate> tradeDates;
    private List<LocalDate> settlementDates;
    private List<BigDecimal> settledQuantities;
    
    /**
     * Inflate compressed lots to list of TaxLot objects
     */
    public List<TaxLot> inflate() {
        if (lotIds == null || lotIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<TaxLot> lots = new ArrayList<>();
        int size = lotIds.size();
        
        for (int i = 0; i < size; i++) {
            TaxLot lot = TaxLot.builder()
                    .lotId(getSafe(lotIds, i))
                    .originalQty(getSafe(originalQtys, i))
                    .remainingQty(getSafe(remainingQtys, i))
                    .costBasis(getSafe(costBases, i))
                    .currentRefPrice(getSafe(currentRefPrices, i))
                    .tradeDate(getSafe(tradeDates, i))
                    .settlementDate(getSafe(settlementDates, i))
                    .settledQuantity(getSafe(settledQuantities, i))
                    .build();
            lots.add(lot);
        }
        
        return lots;
    }
    
    /**
     * Compress list of TaxLot objects to CompressedLots
     */
    public static CompressedLots compress(List<TaxLot> lots) {
        if (lots == null || lots.isEmpty()) {
            return CompressedLots.builder()
                    .lotIds(new ArrayList<>())
                    .originalQtys(new ArrayList<>())
                    .remainingQtys(new ArrayList<>())
                    .costBases(new ArrayList<>())
                    .currentRefPrices(new ArrayList<>())
                    .tradeDates(new ArrayList<>())
                    .settlementDates(new ArrayList<>())
                    .settledQuantities(new ArrayList<>())
                    .build();
        }
        
        CompressedLotsBuilder builder = CompressedLots.builder();
        List<String> lotIds = new ArrayList<>();
        List<BigDecimal> originalQtys = new ArrayList<>();
        List<BigDecimal> remainingQtys = new ArrayList<>();
        List<BigDecimal> costBases = new ArrayList<>();
        List<BigDecimal> currentRefPrices = new ArrayList<>();
        List<LocalDate> tradeDates = new ArrayList<>();
        List<LocalDate> settlementDates = new ArrayList<>();
        List<BigDecimal> settledQuantities = new ArrayList<>();
        
        for (TaxLot lot : lots) {
            lotIds.add(lot.getLotId());
            originalQtys.add(lot.getOriginalQty());
            remainingQtys.add(lot.getRemainingQty());
            costBases.add(lot.getCostBasis());
            currentRefPrices.add(lot.getCurrentRefPrice());
            tradeDates.add(lot.getTradeDate());
            settlementDates.add(lot.getSettlementDate());
            settledQuantities.add(lot.getSettledQuantity());
        }
        
        return builder
                .lotIds(lotIds)
                .originalQtys(originalQtys)
                .remainingQtys(remainingQtys)
                .costBases(costBases)
                .currentRefPrices(currentRefPrices)
                .tradeDates(tradeDates)
                .settlementDates(settlementDates)
                .settledQuantities(settledQuantities)
                .build();
    }
    
    private <T> T getSafe(List<T> list, int index) {
        if (list == null || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }
    
    /**
     * Get count of lots
     */
    public int getLotCount() {
        return lotIds != null ? lotIds.size() : 0;
    }
}
