package com.bank.esps.application.service;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.PositionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing positions
 * Delegates to HotpathPositionService for hotpath/coldpath routing
 */
@Service
public class PositionService {
    
    private static final Logger log = LoggerFactory.getLogger(PositionService.class);
    
    private final HotpathPositionService hotpathPositionService;
    
    public PositionService(HotpathPositionService hotpathPositionService) {
        this.hotpathPositionService = hotpathPositionService;
    }
    
    /**
     * Process trade - delegates to hotpath service which routes to hotpath or coldpath
     */
    @Transactional
    public PositionState processTrade(TradeEvent tradeEvent) {
        return hotpathPositionService.processTrade(tradeEvent);
    }
    
    /**
     * Get current state - delegates to hotpath service
     */
    @Transactional(readOnly = true)
    public PositionState getCurrentState(String positionKey) {
        return hotpathPositionService.getCurrentState(positionKey);
    }
}
