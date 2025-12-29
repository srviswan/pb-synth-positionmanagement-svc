package com.bank.esps.api.controller;

import com.bank.esps.application.service.PositionService;
import com.bank.esps.domain.model.PositionState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for position queries
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {
    
    private final PositionService positionService;
    
    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }
    
    @GetMapping("/{positionKey}")
    public ResponseEntity<PositionState> getPosition(@PathVariable String positionKey) {
        try {
            PositionState position = positionService.getCurrentState(positionKey);
            if (position != null) {
                return ResponseEntity.ok(position);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
