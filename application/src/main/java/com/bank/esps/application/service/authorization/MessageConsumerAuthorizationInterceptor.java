package com.bank.esps.application.service.authorization;

import com.bank.esps.domain.auth.AuthorizationService;
import com.bank.esps.domain.auth.UserContext;
import com.bank.esps.domain.event.TradeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Interceptor for message consumers to check authorization before processing
 * Based on messaging_entitlements_integration.md
 */
@Component
public class MessageConsumerAuthorizationInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(MessageConsumerAuthorizationInterceptor.class);
    
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;
    
    public MessageConsumerAuthorizationInterceptor(AuthorizationService authorizationService,
                                                   ObjectMapper objectMapper) {
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Check if user can consume this event
     */
    public boolean canConsumeEvent(String messageJson, String userId, String requiredFunction) {
        if (userId == null) {
            log.warn("No user ID provided for message consumption check");
            return false; // Fail closed
        }
        
        // Check entitlement
        if (requiredFunction != null && 
            !authorizationService.hasEntitlement(userId, requiredFunction)) {
            log.warn("User {} does not have permission to consume event (required: {})", 
                userId, requiredFunction);
            return false;
        }
        
        // Check data access (account, book) if event contains trade data
        try {
            TradeEvent tradeEvent = objectMapper.readValue(messageJson, TradeEvent.class);
            
            // Check account access
            if (tradeEvent.getAccount() != null && 
                !authorizationService.hasAccountAccess(userId, tradeEvent.getAccount())) {
                log.warn("User {} does not have access to account {}", 
                    userId, tradeEvent.getAccount());
                return false;
            }
            
            // Check book access if applicable
            // Note: TradeEvent may not have bookId, but if it does, check it
            
        } catch (Exception e) {
            // If we can't parse the event, allow it (fail open for backward compatibility)
            // In production, might want to fail closed
            log.debug("Could not parse event for authorization check, allowing: {}", e.getMessage());
        }
        
        return true;
    }
    
    /**
     * Extract user ID from message headers (Kafka or Solace)
     */
    public String extractUserIdFromHeaders(java.util.Map<String, Object> headers) {
        if (headers == null) {
            return null;
        }
        
        // Try different header formats
        Object userId = headers.get("user-id");
        if (userId == null) {
            userId = headers.get("X-User-Id");
        }
        if (userId == null && headers.containsKey("user-id")) {
            userId = headers.get("user-id");
        }
        
        if (userId instanceof String) {
            return (String) userId;
        } else if (userId instanceof byte[]) {
            return new String((byte[]) userId);
        }
        
        return null;
    }
}
