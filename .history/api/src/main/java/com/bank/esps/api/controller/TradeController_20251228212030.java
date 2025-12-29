package com.bank.esps.api.controller;

import com.bank.esps.application.service.PositionService;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.PositionState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for trade operations
 */
@RestController
@RequestMapping("/api/trades")
public class TradeController {
    
    private final PositionService positionService;
    
    public TradeController(PositionService positionService) {
        this.positionService = positionService;
    }
    
    @PostMapping
    public ResponseEntity<PositionState> processTrade(@RequestBody TradeEvent tradeEvent) {
        try {
            PositionState result = positionService.processTrade(tradeEvent);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
