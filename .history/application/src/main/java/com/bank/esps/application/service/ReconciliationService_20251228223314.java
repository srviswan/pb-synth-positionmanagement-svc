package com.bank.esps.application.service;

import com.bank.esps.domain.model.PositionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for reconciliation between internal and external position sources
 * Detects breaks and classifies them by severity
 */
@Service
public class ReconciliationService {
    
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    
    public enum BreakSeverity {
        CRITICAL,  // Quantity mismatch, price mismatch > 5%
        WARNING,   // Price mismatch < 5%, lot count mismatch
        INFO       // Minor discrepancies
    }
    
    /**
     * Reconciliation break result
     */
    public static class ReconciliationBreak {
        private String positionKey;
        private String breakType;
        private BreakSeverity severity;
        private String description;
        private Map<String, Object> internalValue;
        private Map<String, Object> externalValue;
        
        // Getters and setters
        public String getPositionKey() { return positionKey; }
        public void setPositionKey(String positionKey) { this.positionKey = positionKey; }
        public String getBreakType() { return breakType; }
        public void setBreakType(String breakType) { this.breakType = breakType; }
        public BreakSeverity getSeverity() { return severity; }
        public void setSeverity(BreakSeverity severity) { this.severity = severity; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Object> getInternalValue() { return internalValue; }
        public void setInternalValue(Map<String, Object> internalValue) { this.internalValue = internalValue; }
        public Map<String, Object> getExternalValue() { return externalValue; }
        public void setExternalValue(Map<String, Object> externalValue) { this.externalValue = externalValue; }
    }
    
    /**
     * Reconcile internal position with external source
     */
    public List<ReconciliationBreak> reconcile(PositionState internalPosition, 
                                               Map<String, Object> externalPosition) {
        List<ReconciliationBreak> breaks = new ArrayList<>();
        
        if (internalPosition == null || externalPosition == null) {
            ReconciliationBreak break_ = new ReconciliationBreak();
            break_.setPositionKey(internalPosition != null ? internalPosition.getPositionKey() : "UNKNOWN");
            break_.setBreakType("MISSING_DATA");
            break_.setSeverity(BreakSeverity.CRITICAL);
            break_.setDescription("Internal or external position data is missing");
            breaks.add(break_);
            return breaks;
        }
        
        // Compare quantity
        BigDecimal internalQty = internalPosition.getTotalQty();
        BigDecimal externalQty = externalPosition.get("quantity") != null 
                ? new BigDecimal(externalPosition.get("quantity").toString()) 
                : BigDecimal.ZERO;
        
        if (internalQty.compareTo(externalQty) != 0) {
            ReconciliationBreak break_ = new ReconciliationBreak();
            break_.setPositionKey(internalPosition.getPositionKey());
            break_.setBreakType("QUANTITY_MISMATCH");
            break_.setSeverity(BreakSeverity.CRITICAL);
            break_.setDescription(String.format("Quantity mismatch: internal=%s, external=%s", 
                    internalQty, externalQty));
            Map<String, Object> internalVal = new HashMap<>();
            internalVal.put("quantity", internalQty);
            break_.setInternalValue(internalVal);
            Map<String, Object> externalVal = new HashMap<>();
            externalVal.put("quantity", externalQty);
            break_.setExternalValue(externalVal);
            breaks.add(break_);
        }
        
        // Compare lot count
        int internalLotCount = internalPosition.getOpenLots() != null 
                ? internalPosition.getOpenLots().size() 
                : 0;
        int externalLotCount = externalPosition.get("lotCount") != null 
                ? Integer.parseInt(externalPosition.get("lotCount").toString()) 
                : 0;
        
        if (internalLotCount != externalLotCount) {
            ReconciliationBreak break_ = new ReconciliationBreak();
            break_.setPositionKey(internalPosition.getPositionKey());
            break_.setBreakType("LOT_COUNT_MISMATCH");
            break_.setSeverity(BreakSeverity.WARNING);
            break_.setDescription(String.format("Lot count mismatch: internal=%d, external=%d", 
                    internalLotCount, externalLotCount));
            Map<String, Object> internalVal = new HashMap<>();
            internalVal.put("lotCount", internalLotCount);
            break_.setInternalValue(internalVal);
            Map<String, Object> externalVal = new HashMap<>();
            externalVal.put("lotCount", externalLotCount);
            break_.setExternalValue(externalVal);
            breaks.add(break_);
        }
        
        // Compare exposure (if provided)
        if (externalPosition.get("exposure") != null) {
            BigDecimal internalExposure = internalPosition.getExposure();
            BigDecimal externalExposure = new BigDecimal(externalPosition.get("exposure").toString());
            BigDecimal difference = internalExposure.subtract(externalExposure).abs();
            BigDecimal percentDifference = internalExposure.compareTo(BigDecimal.ZERO) != 0
                    ? difference.divide(internalExposure, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO;
            
            if (percentDifference.compareTo(new BigDecimal("5")) > 0) {
                ReconciliationBreak break_ = new ReconciliationBreak();
                break_.setPositionKey(internalPosition.getPositionKey());
                break_.setBreakType("EXPOSURE_MISMATCH");
                break_.setSeverity(BreakSeverity.CRITICAL);
                break_.setDescription(String.format("Exposure mismatch > 5%%: internal=%s, external=%s, diff=%s%%", 
                        internalExposure, externalExposure, percentDifference));
                Map<String, Object> internalVal = new HashMap<>();
                internalVal.put("exposure", internalExposure);
                break_.setInternalValue(internalVal);
                Map<String, Object> externalVal = new HashMap<>();
                externalVal.put("exposure", externalExposure);
                break_.setExternalValue(externalVal);
                breaks.add(break_);
            } else if (percentDifference.compareTo(BigDecimal.ZERO) > 0) {
                ReconciliationBreak break_ = new ReconciliationBreak();
                break_.setPositionKey(internalPosition.getPositionKey());
                break_.setBreakType("EXPOSURE_MISMATCH");
                break_.setSeverity(BreakSeverity.WARNING);
                break_.setDescription(String.format("Exposure mismatch < 5%%: internal=%s, external=%s, diff=%s%%", 
                        internalExposure, externalExposure, percentDifference));
                Map<String, Object> internalVal = new HashMap<>();
                internalVal.put("exposure", internalExposure);
                break_.setInternalValue(internalVal);
                Map<String, Object> externalVal = new HashMap<>();
                externalVal.put("exposure", externalExposure);
                break_.setExternalValue(externalVal);
                breaks.add(break_);
            }
        }
        
        return breaks;
    }
}
