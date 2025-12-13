package com.bank.esps.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Compressed tax lots using parallel arrays pattern
 * Saves 40-60% JSON space vs Array of Objects
 */
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore unknown fields like "empty" during deserialization
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompressedLots {
    @JsonProperty("ids")
    private List<String> ids = new ArrayList<>();
    
    @JsonProperty("dates")
    private List<String> dates = new ArrayList<>();
    
    @JsonProperty("prices")
    private List<BigDecimal> prices = new ArrayList<>();
    
    @JsonProperty("qtys")
    private List<BigDecimal> qtys = new ArrayList<>();
    
    /**
     * Inflate compressed format back to TaxLot objects for processing
     */
    public List<TaxLot> inflate() {
        List<TaxLot> lots = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            lots.add(TaxLot.builder()
                    .id(ids.get(i))
                    .tradeDate(LocalDate.parse(dates.get(i)))
                    .currentRefPrice(prices.get(i))
                    .remainingQty(qtys.get(i))
                    .build());
        }
        return lots;
    }
    
    /**
     * Compress list of TaxLot objects to parallel arrays format
     */
    public static CompressedLots compress(List<TaxLot> lots) {
        CompressedLots compressed = new CompressedLots();
        for (TaxLot lot : lots) {
            compressed.getIds().add(lot.getId());
            compressed.getDates().add(lot.getTradeDate().toString());
            compressed.getPrices().add(lot.getCurrentRefPrice());
            compressed.getQtys().add(lot.getRemainingQty());
        }
        return compressed;
    }
    
    /**
     * Get count of lots
     */
    public int size() {
        return ids.size();
    }
    
    /**
     * Check if empty
     */
    public boolean isEmpty() {
        return ids.isEmpty();
    }
}
