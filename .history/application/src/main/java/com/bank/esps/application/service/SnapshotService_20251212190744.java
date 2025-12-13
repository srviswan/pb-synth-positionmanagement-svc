package com.bank.esps.application.service;

import com.bank.esps.domain.enums.PositionStatus;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.CompressedLots;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.PriceQuantitySchedule;
import com.bank.esps.domain.model.TaxLot;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for managing position snapshots
 * Handles snapshot creation, inflation, compression, and updates
 */
@Service
public class SnapshotService {
    
    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    
    private final ObjectMapper objectMapper;
    
    public SnapshotService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Create a new snapshot for a new position
     */
    public SnapshotEntity createNewSnapshot(String positionKey, TradeEvent tradeEvent) {
        PositionState initialState = new PositionState();
        CompressedLots emptyCompressed = CompressedLots.compress(initialState.getOpenLots());
        
        SnapshotEntity snapshot = new SnapshotEntity();
        snapshot.setPositionKey(positionKey);
        snapshot.setLastVer(0L);
        snapshot.setUti(tradeEvent.getTradeId()); // Initial UPI
        snapshot.setStatus(PositionStatus.ACTIVE);
        snapshot.setReconciliationStatus(ReconciliationStatus.RECONCILED);
        snapshot.setTaxLotsCompressed(toJson(emptyCompressed));
        snapshot.setSummaryMetrics(toJson(createSummaryMetrics(initialState)));
        
        // Initialize PriceQuantity schedule with first trade
        PriceQuantitySchedule schedule = PriceQuantitySchedule.builder()
                .unit("SHARES")
                .currency("USD")
                .build();
        schedule.addOrUpdate(tradeEvent.getEffectiveDate(), tradeEvent.getQuantity(), tradeEvent.getPrice());
        snapshot.setPriceQuantitySchedule(toJson(schedule));
        
        snapshot.setVersion(0L);
        return snapshot;
    }
    
    /**
     * Inflate snapshot to PositionState
     * Handles deserialization of compressed tax lots
     */
    public PositionState inflateSnapshot(SnapshotEntity snapshot) {
        log.info("ENTERING inflateSnapshot for position {}, version {}", snapshot.getPositionKey(), snapshot.getLastVer());
        try {
            if (snapshot.getTaxLotsCompressed() == null || snapshot.getTaxLotsCompressed().trim().isEmpty()) {
                log.debug("Snapshot for position {} has empty tax lots compressed, returning empty state", snapshot.getPositionKey());
                return new PositionState();
            }
            
            String compressedJson = snapshot.getTaxLotsCompressed();
            log.info("Inflating snapshot for position {}, compressed JSON length: {}, first 100 chars: {}", 
                    snapshot.getPositionKey(), compressedJson.length(),
                    compressedJson.length() > 0 ? compressedJson.substring(0, Math.min(100, compressedJson.length())) : "empty");
            
            // Parse JSON and manually construct CompressedLots to avoid "empty" field issue
            log.info("Starting manual JSON parsing for position {}", snapshot.getPositionKey());
            
            JsonNode jsonNode;
            try {
                jsonNode = objectMapper.readTree(compressedJson);
                log.info("Successfully parsed JSON tree, now constructing CompressedLots manually");
            } catch (Exception readTreeException) {
                log.error("CRITICAL: readTree threw exception! This should not happen. Exception: {}", readTreeException.getMessage(), readTreeException);
                throw new RuntimeException("readTree failed for position " + snapshot.getPositionKey(), readTreeException);
            }
            
            CompressedLots compressed = new CompressedLots();
            if (jsonNode.has("ids") && jsonNode.get("ids").isArray()) {
                List<String> ids = new java.util.ArrayList<>();
                for (JsonNode idNode : jsonNode.get("ids")) {
                    ids.add(idNode.asText());
                }
                compressed.setIds(ids);
            }
            if (jsonNode.has("dates") && jsonNode.get("dates").isArray()) {
                List<String> dates = new java.util.ArrayList<>();
                for (JsonNode dateNode : jsonNode.get("dates")) {
                    dates.add(dateNode.asText());
                }
                compressed.setDates(dates);
            }
            if (jsonNode.has("prices") && jsonNode.get("prices").isArray()) {
                List<BigDecimal> prices = new java.util.ArrayList<>();
                for (JsonNode priceNode : jsonNode.get("prices")) {
                    prices.add(priceNode.decimalValue());
                }
                compressed.setPrices(prices);
            }
            if (jsonNode.has("qtys") && jsonNode.get("qtys").isArray()) {
                List<BigDecimal> qtys = new java.util.ArrayList<>();
                for (JsonNode qtyNode : jsonNode.get("qtys")) {
                    qtys.add(qtyNode.decimalValue());
                }
                compressed.setQtys(qtys);
            }
            if (jsonNode.has("originalPrices") && jsonNode.get("originalPrices").isArray()) {
                List<BigDecimal> originalPrices = new java.util.ArrayList<>();
                for (JsonNode priceNode : jsonNode.get("originalPrices")) {
                    originalPrices.add(priceNode.decimalValue());
                }
                compressed.setOriginalPrices(originalPrices);
            }
            if (jsonNode.has("originalQtys") && jsonNode.get("originalQtys").isArray()) {
                List<BigDecimal> originalQtys = new java.util.ArrayList<>();
                for (JsonNode qtyNode : jsonNode.get("originalQtys")) {
                    originalQtys.add(qtyNode.decimalValue());
                }
                compressed.setOriginalQtys(originalQtys);
            }
            
            log.info("Manually constructed CompressedLots with {} ids, {} dates, {} prices, {} qtys", 
                    compressed.getIds().size(), compressed.getDates().size(), 
                    compressed.getPrices().size(), compressed.getQtys().size());
            
            List<TaxLot> inflatedLots = compressed.inflate();
            log.info("Successfully inflated {} lots from manually constructed CompressedLots. Lot details: {}", 
                    inflatedLots.size(),
                    inflatedLots.stream()
                            .map(lot -> String.format("Lot %s: qty=%s, closed=%s", lot.getId(), lot.getRemainingQty(), lot.isClosed()))
                            .collect(java.util.stream.Collectors.joining(", ")));
            
            PositionState state = new PositionState();
            state.setOpenLots(inflatedLots);
            
            // Verify the lots were set correctly
            if (inflatedLots.size() > 0) {
                int rawLotsCount = state.getAllLots().size();
                if (rawLotsCount != inflatedLots.size()) {
                    log.error("MISMATCH: Inflated {} lots but getAllLots() returned {}. This indicates a problem!", 
                            inflatedLots.size(), rawLotsCount);
                }
            }
            
            int openLotsCount = state.getOpenLots().size();
            BigDecimal totalQty = state.getTotalQty();
            log.info("Successfully inflated {} lots from snapshot for position {}: {} open lots, {} total lots, total qty: {}", 
                    inflatedLots.size(), snapshot.getPositionKey(), openLotsCount, state.getAllLots().size(), totalQty);
            
            return state;
        } catch (Exception e) {
            log.error("CRITICAL: Error inflating snapshot for position {}: {}. Exception type: {}. Compressed data: {}", 
                    snapshot.getPositionKey(), e.getMessage(), e.getClass().getName(),
                    snapshot.getTaxLotsCompressed() != null ? 
                        snapshot.getTaxLotsCompressed().substring(0, Math.min(200, snapshot.getTaxLotsCompressed().length())) : "null", e);
            throw new RuntimeException("Failed to inflate snapshot for position " + snapshot.getPositionKey() + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Update snapshot from position state
     * Compresses lots and updates all snapshot fields
     */
    public void updateSnapshot(SnapshotEntity snapshot, PositionState state, 
                               long version, ReconciliationStatus reconciliationStatus, TradeEvent tradeEvent) {
        List<TaxLot> allLots = state.getAllLots();
        int openLotsCount = state.getOpenLots().size();
        BigDecimal totalQty = state.getTotalQty();
        
        log.info("Updating snapshot for position {}: state has {} total lots ({} open), total qty: {}", 
                snapshot.getPositionKey(), allLots.size(), openLotsCount, totalQty);
        
        // Compress lots
        CompressedLots compressed = CompressedLots.compress(allLots);
        String compressedJson = toJson(compressed);
        
        // Verify compression worked
        if (compressedJson == null || compressedJson.trim().isEmpty()) {
            log.error("CRITICAL: Compressed JSON is empty after compressing {} lots!", allLots.size());
            throw new IllegalStateException("Failed to compress lots: JSON is empty");
        }
        
        snapshot.setTaxLotsCompressed(compressedJson);
        snapshot.setLastVer(version);
        snapshot.setReconciliationStatus(reconciliationStatus);
        snapshot.setSummaryMetrics(toJson(createSummaryMetrics(state)));
        
        // Update PriceQuantity schedule
        updatePriceQuantitySchedule(snapshot, state, tradeEvent);
        
        snapshot.setLastUpdatedAt(java.time.OffsetDateTime.now());
    }
    
    /**
     * Update PriceQuantity schedule in snapshot
     */
    private void updatePriceQuantitySchedule(SnapshotEntity snapshot, PositionState state, TradeEvent tradeEvent) {
        try {
            PriceQuantitySchedule schedule;
            if (snapshot.getPriceQuantitySchedule() != null && !snapshot.getPriceQuantitySchedule().trim().isEmpty()) {
                schedule = objectMapper.readValue(snapshot.getPriceQuantitySchedule(), PriceQuantitySchedule.class);
            } else {
                schedule = PriceQuantitySchedule.builder()
                        .unit("SHARES")
                        .currency("USD")
                        .build();
            }
            
            // Calculate current average price from lots
            BigDecimal currentQty = state.getTotalQty();
            BigDecimal currentPrice = BigDecimal.ZERO;
            if (currentQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalNotional = state.getOpenLots().stream()
                        .map(lot -> lot.getRemainingQty().multiply(lot.getCurrentRefPrice()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                currentPrice = totalNotional.divide(currentQty, 4, java.math.RoundingMode.HALF_UP);
            } else {
                currentPrice = tradeEvent.getPrice();
            }
            
            // Add/update schedule entry
            schedule.addOrUpdate(tradeEvent.getEffectiveDate(), currentQty, currentPrice);
            snapshot.setPriceQuantitySchedule(toJson(schedule));
        } catch (Exception e) {
            log.error("Error updating PriceQuantity schedule for position {}", snapshot.getPositionKey(), e);
            // Don't throw - schedule update failure shouldn't block snapshot save
        }
    }
    
    /**
     * Create corrected snapshot for coldpath recalculation
     */
    public SnapshotEntity createCorrectedSnapshot(
            String positionKey,
            PositionState state,
            long newVersion,
            SnapshotEntity currentSnapshot,
            String correctUPI,
            PositionStatus correctStatus) {
        
        SnapshotEntity snapshot = new SnapshotEntity();
        snapshot.setPositionKey(positionKey);
        snapshot.setLastVer(newVersion);
        
        // Determine final status based on current quantity
        if (state.getTotalQty().compareTo(BigDecimal.ZERO) == 0) {
            correctStatus = PositionStatus.TERMINATED;
        } else if (correctStatus == PositionStatus.TERMINATED && 
                   state.getTotalQty().compareTo(BigDecimal.ZERO) > 0) {
            // Position was TERMINATED but backdated trade reopened it
            correctStatus = PositionStatus.ACTIVE;
            log.info("Backdated trade reopened TERMINATED position, restoring UPI: {}", correctUPI);
        }
        
        snapshot.setUti(correctUPI != null ? correctUPI : currentSnapshot.getUti());
        snapshot.setStatus(correctStatus);
        snapshot.setReconciliationStatus(ReconciliationStatus.RECONCILED);
        snapshot.setVersion(currentSnapshot.getVersion() + 1); // Optimistic locking version
        
        log.info("Corrected snapshot UPI: {} (was: {}), Status: {} (was: {})", 
                snapshot.getUti(), currentSnapshot.getUti(), 
                snapshot.getStatus(), currentSnapshot.getStatus());
        
        // Compress lots
        try {
            CompressedLots compressed = CompressedLots.compress(state.getAllLots());
            snapshot.setTaxLotsCompressed(objectMapper.writeValueAsString(compressed));
        } catch (Exception e) {
            log.error("Error compressing lots for corrected snapshot", e);
            throw new RuntimeException("Failed to compress lots", e);
        }
        
        // Update summary metrics
        snapshot.setSummaryMetrics(String.format(
                "{\"totalQty\":%s,\"exposure\":%s,\"lotCount\":%d}",
                state.getTotalQty(), state.getExposure(), state.getLotCount()));
        
        snapshot.setLastUpdatedAt(java.time.OffsetDateTime.now());
        
        return snapshot;
    }
    
    /**
     * Create summary metrics JSON
     */
    private String createSummaryMetrics(PositionState state) {
        try {
            return String.format(
                    "{\"totalQty\":%s,\"exposure\":%s,\"lotCount\":%d}",
                    state.getTotalQty(), state.getExposure(), state.getLotCount());
        } catch (Exception e) {
            log.error("Error creating summary metrics", e);
            return "{}";
        }
    }
    
    /**
     * Convert object to JSON string
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Error converting object to JSON", e);
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
