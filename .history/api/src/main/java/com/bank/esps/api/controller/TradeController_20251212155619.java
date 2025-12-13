package com.bank.esps.api.controller;

import com.bank.esps.application.service.TradeProcessingService;
import com.bank.esps.domain.event.TradeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for submitting trades
 */
@RestController
@RequestMapping("/api/trades")
public class TradeController {
    
    @Autowired
    private TradeProcessingService tradeProcessingService;
    
    /**
     * Submit a trade for processing
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> submitTrade(@RequestBody TradeEvent tradeEvent) {
        Map<String, Object> response = new HashMap<>();
        try {
            tradeProcessingService.processTrade(tradeEvent);
            response.put("status", "success");
            response.put("message", "Trade processed successfully");
            response.put("tradeId", tradeEvent.getTradeId());
            response.put("positionKey", tradeEvent.getPositionKey());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            response.put("tradeId", tradeEvent.getTradeId());
            return ResponseEntity.status(500).body(response);
        }
    }
}
