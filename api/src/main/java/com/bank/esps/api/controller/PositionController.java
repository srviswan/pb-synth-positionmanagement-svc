package com.bank.esps.api.controller;

import com.bank.esps.application.service.PositionService;
import com.bank.esps.api.service.UserContextExtractor;
import com.bank.esps.domain.auth.AuthorizationService;
import com.bank.esps.domain.auth.PositionFunction;
import com.bank.esps.domain.auth.UserContext;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for position queries
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {
    
    private static final Logger log = LoggerFactory.getLogger(PositionController.class);
    
    private final PositionService positionService;
    private final SnapshotRepository snapshotRepository;
    private final AuthorizationService authorizationService;
    private final UserContextExtractor userContextExtractor;
    
    public PositionController(PositionService positionService, 
                             SnapshotRepository snapshotRepository,
                             AuthorizationService authorizationService,
                             UserContextExtractor userContextExtractor) {
        this.positionService = positionService;
        this.snapshotRepository = snapshotRepository;
        this.authorizationService = authorizationService;
        this.userContextExtractor = userContextExtractor;
    }
    
    @GetMapping("/{positionKey}")
    public ResponseEntity<PositionState> getPosition(@PathVariable String positionKey,
                                                      HttpServletRequest request) {
        // User context is checked in AuthorizationFilter
        // Additional data access check here if needed
        UserContext userContext = (UserContext) request.getAttribute("userContext");
        if (userContext != null) {
            PositionState position = positionService.getCurrentState(positionKey);
            if (position != null) {
                // Check account access if position has account
                if (position.getAccount() != null && 
                    !authorizationService.hasAccountAccess(userContext.getUserId(), position.getAccount())) {
                    log.warn("User {} denied access to position {} (account: {})", 
                        userContext.getUserId(), positionKey, position.getAccount());
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
                }
            }
        }
        try {
            PositionState position = positionService.getCurrentState(positionKey);
            if (position != null) {
                return ResponseEntity.ok(position);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting position: {}", positionKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllPositions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reconciliationStatus,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        try {
            // Limit page size
            if (size > 100) {
                size = 100;
            }
            
            Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
            Page<SnapshotEntity> snapshotPage = snapshotRepository.findAll(pageable);
            
            // Filter by status and reconciliation status if provided
            // Note: This is a simplified implementation - full filtering would require custom queries
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", snapshotPage.getContent());
            response.put("totalElements", snapshotPage.getTotalElements());
            response.put("totalPages", snapshotPage.getTotalPages());
            response.put("page", page);
            response.put("size", size);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting all positions", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/{positionKey}/quantity")
    public ResponseEntity<Map<String, Object>> getPositionQuantity(@PathVariable String positionKey) {
        try {
            PositionState position = positionService.getCurrentState(positionKey);
            if (position != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("positionKey", positionKey);
                response.put("quantity", position.getTotalQty());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting position quantity: {}", positionKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/{positionKey}/details")
    public ResponseEntity<Map<String, Object>> getPositionDetails(@PathVariable String positionKey) {
        try {
            PositionState position = positionService.getCurrentState(positionKey);
            if (position != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("positionKey", position.getPositionKey());
                response.put("account", position.getAccount());
                response.put("instrument", position.getInstrument());
                response.put("currency", position.getCurrency());
                response.put("totalQuantity", position.getTotalQty());
                response.put("exposure", position.getExposure());
                response.put("weightedAveragePrice", position.getWeightedAveragePrice());
                response.put("lotCount", position.getOpenLots() != null ? position.getOpenLots().size() : 0);
                response.put("openLots", position.getOpenLots());
                response.put("priceQuantitySchedule", position.getPriceQuantitySchedule());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting position details: {}", positionKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/by-account/{account}")
    public ResponseEntity<Map<String, Object>> getPositionsByAccount(
            @PathVariable String account,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        try {
            if (size > 100) size = 100;
            Pageable pageable = PageRequest.of(page, size);
            Page<SnapshotEntity> snapshotPage = snapshotRepository.findByAccount(account, pageable);
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", snapshotPage.getContent());
            response.put("totalElements", snapshotPage.getTotalElements());
            response.put("page", page);
            response.put("size", size);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting positions by account: {}", account, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/by-instrument/{instrument}")
    public ResponseEntity<Map<String, Object>> getPositionsByInstrument(
            @PathVariable String instrument,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        try {
            if (size > 100) size = 100;
            Pageable pageable = PageRequest.of(page, size);
            Page<SnapshotEntity> snapshotPage = snapshotRepository.findByInstrument(instrument, pageable);
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", snapshotPage.getContent());
            response.put("totalElements", snapshotPage.getTotalElements());
            response.put("page", page);
            response.put("size", size);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting positions by instrument: {}", instrument, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
