package com.bank.esps.application.service;

import com.bank.esps.domain.enums.TradeSequenceStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Classifies trades as CURRENT_DATED, FORWARD_DATED, or BACKDATED
 * based on effective date comparison with latest snapshot
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeClassifier {
    
    private final SnapshotRepository snapshotRepository;
    
    /**
     * Classify trade based on effective date
     */
    public TradeSequenceStatus classifyTrade(TradeEvent trade) {
        LocalDate effectiveDate = trade.getEffectiveDate();
        LocalDate currentDate = LocalDate.now();
        
        // Compare with latest snapshot date
        LocalDate latestSnapshotDate = getLatestSnapshotDate(trade.getPositionKey());
        
        if (effectiveDate.isAfter(currentDate)) {
            trade.setSequenceStatus(TradeSequenceStatus.FORWARD_DATED);
            log.debug("Trade {} classified as FORWARD_DATED (effective: {}, current: {})", 
                    trade.getTradeId(), effectiveDate, currentDate);
            return TradeSequenceStatus.FORWARD_DATED;
        } else if (latestSnapshotDate != null && effectiveDate.isBefore(latestSnapshotDate)) {
            trade.setSequenceStatus(TradeSequenceStatus.BACKDATED);
            log.debug("Trade {} classified as BACKDATED (effective: {}, snapshot: {})", 
                    trade.getTradeId(), effectiveDate, latestSnapshotDate);
            return TradeSequenceStatus.BACKDATED;
        } else {
            trade.setSequenceStatus(TradeSequenceStatus.CURRENT_DATED);
            log.debug("Trade {} classified as CURRENT_DATED (effective: {})", 
                    trade.getTradeId(), effectiveDate);
            return TradeSequenceStatus.CURRENT_DATED;
        }
    }
    
    /**
     * Get latest snapshot date for position
     * Returns null if no snapshot exists
     */
    private LocalDate getLatestSnapshotDate(String positionKey) {
        return snapshotRepository.findById(positionKey)
                .map(snapshot -> snapshot.getLastUpdatedAt().toLocalDate())
                .orElse(null);
    }
}
