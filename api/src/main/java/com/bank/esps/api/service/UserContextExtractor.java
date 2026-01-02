package com.bank.esps.api.service;

import com.bank.esps.domain.auth.AuthorizationService;
import com.bank.esps.domain.auth.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Extracts user context from HTTP request (JWT token, headers, etc.)
 */
@Component
public class UserContextExtractor {
    
    private static final Logger log = LoggerFactory.getLogger(UserContextExtractor.class);
    
    private final AuthorizationService authorizationService;
    
    public UserContextExtractor(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }
    
    /**
     * Extract user context from HTTP request
     * Supports JWT tokens in Authorization header or user-id header
     */
    public UserContext extract(HttpServletRequest request) {
        // Try to extract from JWT token first
        String token = extractToken(request);
        if (token != null) {
            UserContext context = extractFromToken(token);
            if (context != null) {
                return enrichContext(context, request);
            }
        }
        
        // Fallback to header-based extraction
        String userId = request.getHeader("X-User-Id");
        if (userId == null) {
            userId = request.getHeader("user-id");
        }
        
        if (userId != null) {
            // Get full context from authorization service
            UserContext context = authorizationService.getUserContext(userId);
            return enrichContext(context, request);
        }
        
        // Development mode: create anonymous context
        log.warn("No user context found in request, creating anonymous context");
        return createAnonymousContext(request);
    }
    
    /**
     * Extract JWT token from Authorization header
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
    
    /**
     * Extract user context from JWT token
     * In a real implementation, would decode and validate JWT
     */
    private UserContext extractFromToken(String token) {
        try {
            // TODO: Decode JWT token and extract claims
            // For now, return null to fall back to header-based extraction
            // In production, use a JWT library like jjwt or nimbus-jose-jwt
            
            // Example structure (would be decoded from JWT):
            // {
            //   "sub": "user123",
            //   "email": "user@example.com",
            //   "roles": ["TRADE_CAPTURE_USER", "POSITION_VIEWER"],
            //   "permissions": ["trade:create", "position:read"],
            //   "account_ids": ["ACC001", "ACC002"],
            //   "book_ids": ["BOOK001"]
            // }
            
            return null; // Not implemented yet
        } catch (Exception e) {
            log.error("Error extracting user context from token", e);
            return null;
        }
    }
    
    /**
     * Enrich user context with request metadata
     */
    private UserContext enrichContext(UserContext context, HttpServletRequest request) {
        if (context == null) {
            return null;
        }
        
        context.setClientIp(getClientIp(request));
        context.setSessionId(request.getSession(false) != null ? 
            request.getSession(false).getId() : null);
        
        return context;
    }
    
    /**
     * Create anonymous context for development/testing
     */
    private UserContext createAnonymousContext(HttpServletRequest request) {
        return new UserContext(
            "anonymous",
            "anonymous",
            null,
            Collections.singletonList("ANONYMOUS"),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
