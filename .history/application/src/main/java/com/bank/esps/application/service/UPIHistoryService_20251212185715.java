package com.bank.esps.application.service;

import com.bank.esps.domain.enums.PositionStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.persistence.entity.UPIHistoryEntity;
import com.bank.esps.infrastructure.persistence.repository.UPIHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for tracking UPI history and detecting merges
 */
@Service
public class UPIHistoryService {
    
    private static final Logger log = LoggerFactory.getLogger(UPIHistoryService.class);
    
    private final UPIHistoryRepository upiHistoryRepository;
    
    public UPIHistoryService(UPIHistoryRepository upiHistoryRepository) {
        this.upiHistoryRepository = upiHistoryRepository;
    }
    
    /**
     * Record UPI change in history
     */
    @Transactional
    public void recordUPIChange(
            String positionKey,
            String currentUPI,
            String previousUPI,
            PositionStatus currentStatus,
            PositionStatus previousStatus,
            String changeType,
            TradeEvent triggeringTrade,
            String reason) {
        
        try {
            UPIHistoryEntity history = UPIHistoryEntity.builder()
                    .positionKey(positionKey)
                    .upi(currentUPI)
                    .previousUPI(previousUPI)
                    .status(currentStatus)
                    .previousStatus(previousStatus)
                    .changeType(changeType)
                    .triggeringTradeId(triggeringTrade != null ? triggeringTrade.getTradeId() : null)
                    .backdatedTradeId(triggeringTrade != null && 
                            triggeringTrade.getSequenceStatus() != null &&
                            triggeringTrade.getSequenceStatus().name().equals("BACKDATED") ? 
                            triggeringTrade.getTradeId() : null)
                    .effectiveDate(triggeringTrade != null ? triggeringTrade.getEffectiveDate() : 
                            java.time.LocalDate.now())
                    .occurredAt(OffsetDateTime.now())
                    .reason(reason)
                    .build();
            
            upiHistoryRepository.save(history);
            log.info("Recorded UPI change: position={}, UPI={} -> {}, type={}, reason={}", 
                    positionKey, previousUPI, currentUPI, changeType, reason);
            
        } catch (Exception e) {
            log.error("Error recording UPI change for position {}", positionKey, e);
            // Don't throw - history tracking failure shouldn't block processing
        }
    }
    
    /**
     * Record UPI creation (first trade on a position)
     */
    @Transactional
    public void recordUPICreation(String positionKey, String upi, TradeEvent tradeEvent) {
        recordUPIChange(positionKey, upi, null, PositionStatus.ACTIVE, null, 
                "CREATED", tradeEvent, 
                String.format("UPI created with first trade %s", tradeEvent.getTradeId()));
    }
    
    /**
     * Record UPI termination (position closed)
     */
    @Transactional
    public void recordUPITermination(String positionKey, String upi, TradeEvent tradeEvent) {
        UPIHistoryEntity previousHistory = upiHistoryRepository.findMostRecentByPositionKey(positionKey)
                .orElse(null);
        
        PositionStatus previousStatus = previousHistory != null ? previousHistory.getStatus() : PositionStatus.ACTIVE;
        
        recordUPIChange(positionKey, upi, upi, PositionStatus.TERMINATED, previousStatus, 
                "TERMINATED", tradeEvent, 
                String.format("UPI terminated: position quantity reached zero (trade %s)", tradeEvent.getTradeId()));
    }
    
    /**
     * Record UPI reopening (new trade on terminated position)
     */
    @Transactional
    public void recordUPIReopening(String positionKey, String newUPI, String previousUPI, TradeEvent tradeEvent) {
        recordUPIChange(positionKey, newUPI, previousUPI, PositionStatus.ACTIVE, PositionStatus.TERMINATED, 
                "REOPENED", tradeEvent, 
                String.format("UPI reopened: new trade %s on terminated position, new UPI=%s", 
                        tradeEvent.getTradeId(), newUPI));
    }
    
    /**
     * Record UPI invalidation (UPI-2 invalidated, restored to UPI-1)
     */
    @Transactional
    public void recordUPIInvalidation(String positionKey, String invalidatedUPI, String restoredUPI, 
                                      TradeEvent backdatedTrade, String reason) {
        recordUPIChange(positionKey, restoredUPI, invalidatedUPI, PositionStatus.ACTIVE, PositionStatus.ACTIVE, 
                "INVALIDATED", backdatedTrade, 
                String.format("UPI %s invalidated and restored to %s: %s", invalidatedUPI, restoredUPI, reason));
    }
    
    /**
     * Record UPI restoration (backdated trade restores original UPI)
     */
    @Transactional
    public void recordUPIRestoration(String positionKey, String restoredUPI, String previousUPI, 
                                     TradeEvent backdatedTrade, String reason) {
        recordUPIChange(positionKey, restoredUPI, previousUPI, PositionStatus.ACTIVE, PositionStatus.TERMINATED, 
                "RESTORED", backdatedTrade, 
                String.format("UPI %s restored from %s: %s", restoredUPI, previousUPI, reason));
    }
    
    /**
     * Detect and record UPI merge
     * A merge occurs when a backdated trade affects multiple positions that should be combined
     * This happens when a backdated trade causes two separate positions to have the same UPI
     */
    @Transactional
    public boolean detectAndRecordMerge(String positionKey, String currentUPI, TradeEvent backdatedTrade) {
        try {
            // Check if there are other positions with the same UPI that should be merged
            // Get the most recent UPI history for other positions with this UPI
            List<UPIHistoryEntity> allUPIHistory = upiHistoryRepository.findByUpiOrderByOccurredAtDesc(currentUPI);
            
            // Group by position key and get most recent for each
            java.util.Map<String, UPIHistoryEntity> latestByPosition = allUPIHistory.stream()
                    .filter(h -> !h.getPositionKey().equals(positionKey))
                    .collect(java.util.stream.Collectors.toMap(
                            UPIHistoryEntity::getPositionKey,
                            h -> h,
                            (existing, replacement) -> existing.getOccurredAt().isAfter(replacement.getOccurredAt()) ? existing : replacement
                    ));
            
            List<UPIHistoryEntity> otherPositionsWithSameUPI = new java.util.ArrayList<>(latestByPosition.values());
            
            if (!otherPositionsWithSameUPI.isEmpty()) {
                // Found potential merge - record it
                for (UPIHistoryEntity otherPosition : otherPositionsWithSameUPI) {
                    String mergedFromPositionKey = otherPosition.getPositionKey();
                    
                    // Record merge for the target position
                    UPIHistoryEntity mergeHistory = UPIHistoryEntity.builder()
                            .positionKey(positionKey)
                            .upi(currentUPI)
                            .previousUPI(currentUPI) // UPI stays the same, but position is merged
                            .status(PositionStatus.ACTIVE)
                            .previousStatus(PositionStatus.ACTIVE)
                            .changeType("MERGED")
                            .triggeringTradeId(backdatedTrade.getTradeId())
                            .backdatedTradeId(backdatedTrade.getTradeId())
                            .effectiveDate(backdatedTrade.getEffectiveDate())
                            .occurredAt(OffsetDateTime.now())
                            .mergedFromPositionKey(mergedFromPositionKey)
                            .reason(String.format("Position merged from %s due to backdated trade %s", 
                                    mergedFromPositionKey, backdatedTrade.getTradeId()))
                            .build();
                    
                    upiHistoryRepository.save(mergeHistory);
                    log.warn("Detected UPI merge: position {} merged from position {} due to backdated trade {}", 
                            positionKey, mergedFromPositionKey, backdatedTrade.getTradeId());
                }
                
                return true; // Merge detected
            }
            
            return false; // No merge detected
            
        } catch (Exception e) {
            log.error("Error detecting UPI merge for position {}", positionKey, e);
            return false;
        }
    }
    
    /**
     * Get UPI history for a position
     */
    public List<UPIHistoryEntity> getUPIHistory(String positionKey) {
        return upiHistoryRepository.findByPositionKeyOrderByOccurredAtDesc(positionKey);
    }
    
    /**
     * Get all merge events
     */
    public List<UPIHistoryEntity> getMergeEvents() {
        return upiHistoryRepository.findMergeEvents();
    }
}
