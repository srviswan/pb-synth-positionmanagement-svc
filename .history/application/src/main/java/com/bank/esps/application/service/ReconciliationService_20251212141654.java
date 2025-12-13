package com.bank.esps.application.service;

import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.model.CompressedLots;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.TaxLot;
import com.bank.esps.infrastructure.persistence.entity.ReconciliationBreakEntity;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.ReconciliationBreakRepository;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reconciliation Service
 * Compares internal positions with external sources and detects breaks
 */
@Service
public class ReconciliationService {
    
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    
    private final SnapshotRepository snapshotRepository;
    private final ReconciliationBreakRepository breakRepository;
    private final ObjectMapper objectMapper;
    
    public ReconciliationService(
            SnapshotRepository snapshotRepository,
            ReconciliationBreakRepository breakRepository,
            ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.breakRepository = breakRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Reconcile position with external source
     * This is a placeholder - in production would call custodian/clearing house API
     */
    @Transactional
    public void reconcilePosition(String positionKey, ExternalPosition externalPosition) {
        log.info("Starting reconciliation for position {}", positionKey);
        
        Optional<SnapshotEntity> snapshotOpt = snapshotRepository.findById(positionKey);
        if (snapshotOpt.isEmpty()) {
            log.warn("No snapshot found for position {}", positionKey);
            return;
        }
        
        SnapshotEntity snapshot = snapshotOpt.get();
        PositionState internalState = inflateSnapshot(snapshot);
        
        // Compare internal vs external
        ReconciliationResult result = comparePositions(internalState, externalPosition);
        
        if (result.hasBreaks()) {
            log.warn("Reconciliation breaks detected for position {}: {}", positionKey, result.getBreaks());
            createBreakRecord(positionKey, result);
        } else {
            log.info("Position {} reconciled successfully", positionKey);
            // Update reconciliation status if it was provisional
            if (snapshot.getReconciliationStatus() == ReconciliationStatus.PROVISIONAL) {
                snapshot.setReconciliationStatus(ReconciliationStatus.RECONCILED);
                snapshotRepository.save(snapshot);
            }
        }
    }
    
    /**
     * Scheduled reconciliation job (runs hourly)
     */
    @Scheduled(cron = "0 0 * * * ?") // Every hour
    public void scheduledReconciliation() {
        log.info("Starting scheduled reconciliation");
        
        // Find all positions that need reconciliation
        List<SnapshotEntity> provisionalSnapshots = snapshotRepository
                .findAllByReconciliationStatus(ReconciliationStatus.PROVISIONAL);
        
        log.info("Found {} provisional positions to reconcile", provisionalSnapshots.size());
        
        for (SnapshotEntity snapshot : provisionalSnapshots) {
            try {
                // In production, would fetch external position from custodian API
                // For now, just log
                log.debug("Reconciling position {}", snapshot.getPositionKey());
                // reconcilePosition(snapshot.getPositionKey(), fetchExternalPosition(...));
            } catch (Exception e) {
                log.error("Error reconciling position {}", snapshot.getPositionKey(), e);
            }
        }
    }
    
    /**
     * Compare internal and external positions
     */
    private ReconciliationResult comparePositions(PositionState internal, ExternalPosition external) {
        ReconciliationResult result = new ReconciliationResult();
        
        // Compare quantity
        BigDecimal qtyDiff = internal.getTotalQty().subtract(external.getTotalQty());
        if (qtyDiff.abs().compareTo(BigDecimal.valueOf(0.01)) > 0) {
            result.addBreak("QUANTITY_MISMATCH", 
                    String.format("Internal: %s, External: %s, Diff: %s", 
                            internal.getTotalQty(), external.getTotalQty(), qtyDiff));
        }
        
        // Compare lot count
        if (internal.getLotCount() != external.getLotCount()) {
            result.addBreak("LOT_COUNT_MISMATCH",
                    String.format("Internal: %d, External: %d", 
                            internal.getLotCount(), external.getLotCount()));
        }
        
        // Compare exposure (if available)
        if (external.getExposure() != null) {
            BigDecimal exposureDiff = internal.getExposure().subtract(external.getExposure());
            if (exposureDiff.abs().compareTo(BigDecimal.valueOf(0.01)) > 0) {
                result.addBreak("EXPOSURE_MISMATCH",
                        String.format("Internal: %s, External: %s, Diff: %s",
                                internal.getExposure(), external.getExposure(), exposureDiff));
            }
        }
        
        return result;
    }
    
    /**
     * Create break record
     */
    private void createBreakRecord(String positionKey, ReconciliationResult result) {
        String breakType = result.getBreaks().keySet().iterator().next();
        String description = String.join("; ", result.getBreaks().values());
        
        ReconciliationBreakEntity breakEntity = ReconciliationBreakEntity.builder()
                .positionKey(positionKey)
                .breakType(breakType)
                .severity("WARNING") // Could be CRITICAL, WARNING, INFO
                .status("OPEN")
                .internalValue("{\"description\":\"" + description + "\"}")
                .detectedAt(OffsetDateTime.now())
                .build();
        
        breakRepository.save(breakEntity);
        log.info("Created break record for position {}: {}", positionKey, breakType);
    }
    
    /**
     * Inflate snapshot to PositionState
     */
    private PositionState inflateSnapshot(SnapshotEntity snapshot) {
        if (snapshot.getTaxLotsCompressed() == null || snapshot.getTaxLotsCompressed().trim().isEmpty()) {
            return new PositionState();
        }
        
        try {
            CompressedLots compressed = objectMapper.readValue(
                    snapshot.getTaxLotsCompressed(), 
                    CompressedLots.class);
            List<TaxLot> lots = compressed.inflate();
            
            PositionState state = new PositionState();
            state.setOpenLots(lots);
            return state;
        } catch (Exception e) {
            log.error("Error inflating snapshot for position {}", snapshot.getPositionKey(), e);
            return new PositionState();
        }
    }
    
    /**
     * External position representation (placeholder)
     */
    public static class ExternalPosition {
        private BigDecimal totalQty;
        private int lotCount;
        private BigDecimal exposure;
        
        // Getters and setters
        public BigDecimal getTotalQty() { return totalQty; }
        public void setTotalQty(BigDecimal totalQty) { this.totalQty = totalQty; }
        public int getLotCount() { return lotCount; }
        public void setLotCount(int lotCount) { this.lotCount = lotCount; }
        public BigDecimal getExposure() { return exposure; }
        public void setExposure(BigDecimal exposure) { this.exposure = exposure; }
    }
    
    /**
     * Reconciliation result
     */
    private static class ReconciliationResult {
        private final java.util.Map<String, String> breaks = new java.util.HashMap<>();
        
        public void addBreak(String type, String description) {
            breaks.put(type, description);
        }
        
        public boolean hasBreaks() {
            return !breaks.isEmpty();
        }
        
        public java.util.Map<String, String> getBreaks() {
            return breaks;
        }
    }
}
