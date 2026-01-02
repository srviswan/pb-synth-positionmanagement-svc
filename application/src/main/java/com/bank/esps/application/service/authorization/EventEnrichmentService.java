package com.bank.esps.application.service.authorization;

import com.bank.esps.domain.auth.AuthorizationService;
import com.bank.esps.domain.auth.EntitlementMetadata;
import com.bank.esps.domain.auth.UserContext;
import com.bank.esps.domain.event.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service for enriching events with user context and entitlement metadata
 * Based on messaging_entitlements_integration.md
 */
@Service
public class EventEnrichmentService {
    
    private static final Logger log = LoggerFactory.getLogger(EventEnrichmentService.class);
    
    private final AuthorizationService authorizationService;
    
    public EventEnrichmentService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }
    
    /**
     * Determine required function based on event type or operation
     */
    public String determineRequiredFunction(String eventType, TradeEvent tradeEvent) {
        // Map event type to required function
        if (eventType == null && tradeEvent != null) {
            // Infer from trade event
            if (tradeEvent.getQuantity() != null) {
                if (tradeEvent.getQuantity().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    return "trade:create";
                } else {
                    return "trade:decrease";
                }
            }
            return "trade:create";
        }
        
        // Map common event types
        switch (eventType != null ? eventType.toUpperCase() : "") {
            case "TRADE_CREATED":
            case "TRADE_CREATE":
                return "trade:create";
            case "TRADE_INCREASED":
            case "TRADE_INCREASE":
                return "trade:increase";
            case "TRADE_DECREASED":
            case "TRADE_DECREASE":
                return "trade:decrease";
            case "TRADE_TERMINATED":
            case "TRADE_TERMINATE":
                return "trade:terminate";
            case "POSITION_UPDATED":
            case "POSITION_UPDATE":
                return "position:update";
            default:
                return "trade:create"; // Default
        }
    }
    
    /**
     * Create entitlement metadata for an event
     */
    public EntitlementMetadata createEntitlementMetadata(String requiredFunction, 
                                                         UserContext userContext,
                                                         boolean preAuthorized) {
        EntitlementMetadata metadata = new EntitlementMetadata(requiredFunction);
        metadata.setRequiredPermission(requiredFunction);
        metadata.setAuthorizationStatus(preAuthorized ? "AUTHORIZED" : "PENDING");
        metadata.setAuthorizedAt(LocalDateTime.now());
        metadata.setAuthorizedBy(userContext != null ? userContext.getUserId() : "SYSTEM");
        
        return metadata;
    }
}
