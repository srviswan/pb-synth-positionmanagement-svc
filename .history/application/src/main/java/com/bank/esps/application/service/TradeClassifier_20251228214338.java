package com.bank.esps.application.service;

import com.bank.esps.domain.enums.TradeSequenceStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.TaxLot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Classifies trades based on effective date vs snapshot date
 */
@Component
public class TradeClassifier {
    
    private static final Logger log = LoggerFactory.getLogger(TradeClassifier.class);
    
    /**
     * Classify trade as CURRENT_DATED, FORWARD_DATED, or BACKDATED
     */
    public TradeSequenceStatus classifyTrade(TradeEvent tradeEvent, PositionState currentState) {
        LocalDate effectiveDate = tradeEvent.getEffectiveDate() != null 
                ? tradeEvent.getEffectiveDate() 
                : tradeEvent.getTradeDate();
        
        if (effectiveDate == null) {
            log.warn("Trade {} has no effective date, defaulting to CURRENT_DATED", tradeEvent.getTradeId());
            return TradeSequenceStatus.CURRENT_DATED;
        }
        
        LocalDate today = LocalDate.now();
        LocalDate latestSnapshotDate = getLatestSnapshotDate(currentState);
        
        // Forward-dated: effective date > today
        if (effectiveDate.isAfter(today)) {
            log.debug("Trade {} classified as FORWARD_DATED (effective: {}, today: {})", 
                    tradeEvent.getTradeId(), effectiveDate, today);
            return TradeSequenceStatus.FORWARD_DATED;
        }
        
        // Backdated: effective date < latest snapshot date
        if (latestSnapshotDate != null && effectiveDate.isBefore(latestSnapshotDate)) {
            log.debug("Trade {} classified as BACKDATED (effective: {}, snapshot: {})", 
                    tradeEvent.getTradeId(), effectiveDate, latestSnapshotDate);
            return TradeSequenceStatus.BACKDATED;
        }
        
        // Current-dated: effective date >= latest snapshot date and <= today
        log.debug("Trade {} classified as CURRENT_DATED (effective: {}, snapshot: {})", 
                tradeEvent.getTradeId(), effectiveDate, latestSnapshotDate);
        return TradeSequenceStatus.CURRENT_DATED;
    }
    
    /**
     * Get the latest effective date from position's tax lots
     */
    private LocalDate getLatestSnapshotDate(PositionState state) {
        if (state == null || state.getOpenLots() == null || state.getOpenLots().isEmpty()) {
            return null;
        }
        
        return state.getOpenLots().stream()
                .filter(lot -> lot.getTradeDate() != null)
                .map(TaxLot::getTradeDate)
                .max(LocalDate::compareTo)
                .orElse(null);
    }
}
