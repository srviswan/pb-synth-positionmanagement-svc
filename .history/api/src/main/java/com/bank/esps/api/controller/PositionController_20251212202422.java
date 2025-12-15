package com.bank.esps.api.controller;

import com.bank.esps.application.service.SnapshotService;
import com.bank.esps.domain.enums.PositionStatus;
import com.bank.esps.domain.enums.ReconciliationStatus;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.PriceQuantitySchedule;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for querying position data
 * Provides endpoints for consumers to access position information
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {
    
    private static final Logger log = LoggerFactory.getLogger(PositionController.class);
    
    private final SnapshotRepository snapshotRepository;
    private final SnapshotService snapshotService;
    private final ObjectMapper objectMapper;
    
    public PositionController(
            SnapshotRepository snapshotRepository,
            SnapshotService snapshotService,
            ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.snapshotService = snapshotService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Get position by position key
     * 
     * @param positionKey The position key (hash of account+instrument+currency)
     * @return Position details including quantity, lots, status, UPI, etc.
     */
    @GetMapping("/{positionKey}")
    public ResponseEntity<Map<String, Object>> getPosition(@PathVariable String positionKey) {
        log.info("Querying position for key: {}", positionKey);
        
        Optional<SnapshotEntity> snapshotOpt = snapshotRepository.findById(positionKey);
        
        if (snapshotOpt.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "not_found");
            response.put("message", "Position not found");
            response.put("positionKey", positionKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        
        SnapshotEntity snapshot = snapshotOpt.get();
        return ResponseEntity.ok(buildPositionResponse(snapshot));
    }
    
    /**
     * Get all positions with optional filtering
     * 
     * @param status Optional filter by position status (ACTIVE, TERMINATED)
     * @param reconciliationStatus Optional filter by reconciliation status
     * @param includeArchived Optional flag to include archived positions (default: false)
     * @return List of positions
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllPositions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reconciliationStatus,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
        
        log.info("Querying all positions - status: {}, reconciliationStatus: {}, includeArchived: {}", 
                status, reconciliationStatus, includeArchived);
        
        List<SnapshotEntity> snapshots;
        
        if (reconciliationStatus != null) {
            try {
                ReconciliationStatus reconStatus = ReconciliationStatus.valueOf(reconciliationStatus.toUpperCase());
                snapshots = snapshotRepository.findAllByReconciliationStatus(reconStatus);
            } catch (IllegalArgumentException e) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Invalid reconciliation status: " + reconciliationStatus);
                return ResponseEntity.badRequest().body(response);
            }
        } else {
            snapshots = snapshotRepository.findAll();
        }
        
        // Filter by status if provided
        if (status != null) {
            try {
                PositionStatus positionStatus = PositionStatus.valueOf(status.toUpperCase());
                snapshots = snapshots.stream()
                        .filter(s -> s.getStatus() == positionStatus)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Invalid position status: " + status);
                return ResponseEntity.badRequest().body(response);
            }
        }
        
        // Filter archived if not including
        if (!includeArchived) {
            snapshots = snapshots.stream()
                    .filter(s -> s.getArchivalFlag() == null || !s.getArchivalFlag())
                    .collect(Collectors.toList());
        }
        
        List<Map<String, Object>> positions = snapshots.stream()
                .map(this::buildPositionSummary)
                .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("count", positions.size());
        response.put("positions", positions);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get position by UPI (Unique Position Identifier)
     * 
     * @param upi The UPI to search for
     * @return Position details
     */
    @GetMapping("/upi/{upi}")
    public ResponseEntity<Map<String, Object>> getPositionByUPI(@PathVariable String upi) {
        log.info("Querying position by UPI: {}", upi);
        
        List<SnapshotEntity> snapshots = snapshotRepository.findAll();
        Optional<SnapshotEntity> snapshotOpt = snapshots.stream()
                .filter(s -> upi.equals(s.getUti()))
                .filter(s -> s.getArchivalFlag() == null || !s.getArchivalFlag())
                .findFirst();
        
        if (snapshotOpt.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "not_found");
            response.put("message", "Position not found for UPI: " + upi);
            response.put("upi", upi);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        
        return ResponseEntity.ok(buildPositionResponse(snapshotOpt.get()));
    }
    
    /**
     * Get position quantity and basic metrics
     * Lightweight endpoint for quick position checks
     * 
     * @param positionKey The position key
     * @return Position quantity and basic metrics
     */
    @GetMapping("/{positionKey}/quantity")
    public ResponseEntity<Map<String, Object>> getPositionQuantity(@PathVariable String positionKey) {
        log.info("Querying position quantity for key: {}", positionKey);
        
        Optional<SnapshotEntity> snapshotOpt = snapshotRepository.findById(positionKey);
        
        if (snapshotOpt.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "not_found");
            response.put("message", "Position not found");
            response.put("positionKey", positionKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        
        SnapshotEntity snapshot = snapshotOpt.get();
        PositionState state = snapshotService.inflateSnapshot(snapshot);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("positionKey", positionKey);
        response.put("quantity", state.getTotalQty());
        response.put("openLots", state.getOpenLots().size());
        response.put("totalLots", state.getAllLots().size());
        response.put("exposure", state.getExposure());
        response.put("status", snapshot.getStatus().toString());
        response.put("upi", snapshot.getUti());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get position with full details including tax lots
     * 
     * @param positionKey The position key
     * @return Full position details including all tax lots
     */
    @GetMapping("/{positionKey}/details")
    public ResponseEntity<Map<String, Object>> getPositionDetails(@PathVariable String positionKey) {
        log.info("Querying full position details for key: {}", positionKey);
        
        Optional<SnapshotEntity> snapshotOpt = snapshotRepository.findById(positionKey);
        
        if (snapshotOpt.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "not_found");
            response.put("message", "Position not found");
            response.put("positionKey", positionKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        
        SnapshotEntity snapshot = snapshotOpt.get();
        PositionState state = snapshotService.inflateSnapshot(snapshot);
        
        Map<String, Object> response = buildPositionResponse(snapshot);
        
        // Add detailed lot information
        List<Map<String, Object>> lots = state.getAllLots().stream()
                .map(lot -> {
                    Map<String, Object> lotMap = new HashMap<>();
                    lotMap.put("id", lot.getId());
                    lotMap.put("tradeDate", lot.getTradeDate().toString());
                    lotMap.put("originalPrice", lot.getOriginalPrice());
                    lotMap.put("currentRefPrice", lot.getCurrentRefPrice());
                    lotMap.put("remainingQty", lot.getRemainingQty());
                    lotMap.put("originalQty", lot.getOriginalQty());
                    lotMap.put("isOpen", lot.getRemainingQty().compareTo(BigDecimal.ZERO) > 0);
                    return lotMap;
                })
                .collect(Collectors.toList());
        
        response.put("taxLots", lots);
        response.put("openLots", state.getOpenLots().size());
        response.put("closedLots", state.getAllLots().size() - state.getOpenLots().size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Build full position response from snapshot
     */
    private Map<String, Object> buildPositionResponse(SnapshotEntity snapshot) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("positionKey", snapshot.getPositionKey());
        response.put("upi", snapshot.getUti());
        response.put("status", snapshot.getStatus().toString());
        response.put("reconciliationStatus", snapshot.getReconciliationStatus().toString());
        response.put("lastVersion", snapshot.getLastVer());
        response.put("lastUpdatedAt", snapshot.getLastUpdatedAt().toString());
        response.put("archivalFlag", snapshot.getArchivalFlag() != null ? snapshot.getArchivalFlag() : false);
        if (snapshot.getArchivedAt() != null) {
            response.put("archivedAt", snapshot.getArchivedAt().toString());
        }
        
        // Inflate snapshot to get position state
        PositionState state = snapshotService.inflateSnapshot(snapshot);
        
        // Add quantity and metrics
        response.put("quantity", state.getTotalQty());
        response.put("exposure", state.getExposure());
        response.put("openLotsCount", state.getOpenLots().size());
        response.put("totalLotsCount", state.getAllLots().size());
        
        // Add summary metrics if available
        if (snapshot.getSummaryMetrics() != null && !snapshot.getSummaryMetrics().trim().isEmpty()) {
            try {
                Map<String, Object> summaryMetrics = objectMapper.readValue(
                        snapshot.getSummaryMetrics(), 
                        Map.class);
                response.put("summaryMetrics", summaryMetrics);
            } catch (Exception e) {
                log.warn("Failed to parse summary metrics for position {}", snapshot.getPositionKey(), e);
            }
        }
        
        // Add PriceQuantity schedule if available
        if (snapshot.getPriceQuantitySchedule() != null && !snapshot.getPriceQuantitySchedule().trim().isEmpty()) {
            try {
                Map<String, Object> schedule = objectMapper.readValue(
                        snapshot.getPriceQuantitySchedule(), 
                        Map.class);
                response.put("priceQuantitySchedule", schedule);
            } catch (Exception e) {
                log.warn("Failed to parse PriceQuantity schedule for position {}", snapshot.getPositionKey(), e);
            }
        }
        
        return response;
    }
    
    /**
     * Build summary response for list endpoints
     */
    private Map<String, Object> buildPositionSummary(SnapshotEntity snapshot) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("positionKey", snapshot.getPositionKey());
        summary.put("upi", snapshot.getUti());
        summary.put("status", snapshot.getStatus().toString());
        summary.put("reconciliationStatus", snapshot.getReconciliationStatus().toString());
        summary.put("lastUpdatedAt", snapshot.getLastUpdatedAt().toString());
        
        // Get basic quantity info
        try {
            PositionState state = snapshotService.inflateSnapshot(snapshot);
            summary.put("quantity", state.getTotalQty());
            summary.put("exposure", state.getExposure());
        } catch (Exception e) {
            log.warn("Failed to inflate snapshot for position {} in summary", snapshot.getPositionKey(), e);
            summary.put("quantity", BigDecimal.ZERO);
            summary.put("exposure", BigDecimal.ZERO);
        }
        
        return summary;
    }
}
