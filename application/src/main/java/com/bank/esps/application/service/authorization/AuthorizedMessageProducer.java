package com.bank.esps.application.service.authorization;

import com.bank.esps.domain.auth.AuthorizationService;
import com.bank.esps.domain.auth.UserContext;
import com.bank.esps.domain.messaging.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wrapper around MessageProducer that adds authorization checks before sending
 * Based on messaging_entitlements_integration.md
 */
@Service
public class AuthorizedMessageProducer {
    
    private static final Logger log = LoggerFactory.getLogger(AuthorizedMessageProducer.class);
    
    private final MessageProducer messageProducer;
    private final AuthorizationService authorizationService;
    private final EventEnrichmentService eventEnrichmentService;
    
    public AuthorizedMessageProducer(MessageProducer messageProducer,
                                    AuthorizationService authorizationService,
                                    EventEnrichmentService eventEnrichmentService) {
        this.messageProducer = messageProducer;
        this.authorizationService = authorizationService;
        this.eventEnrichmentService = eventEnrichmentService;
    }
    
    /**
     * Send message with authorization check
     */
    public void sendAuthorized(String topic, String key, Object message, 
                              UserContext userContext, String requiredFunction) {
        if (userContext == null || userContext.getUserId() == null) {
            log.warn("No user context provided for message send, allowing (development mode)");
            messageProducer.send(topic, key, message, null);
            return;
        }
        
        // Check entitlement before publishing
        if (requiredFunction != null && 
            !authorizationService.hasEntitlement(userContext.getUserId(), requiredFunction)) {
            log.warn("User {} denied permission to publish to {} (required: {})", 
                userContext.getUserId(), topic, requiredFunction);
            throw new SecurityException(
                "User " + userContext.getUserId() + " does not have permission: " + requiredFunction
            );
        }
        
        // Send with user context
        messageProducer.send(topic, key, message, userContext);
    }
    
    /**
     * Send message without explicit authorization check (for internal/system messages)
     */
    public void send(String topic, String key, Object message) {
        messageProducer.send(topic, key, message);
    }
}
