package com.bank.esps.domain.auth;

import java.util.List;

/**
 * Authorization service interface for checking user entitlements
 * Integrates with IAM service for authorization decisions
 */
public interface AuthorizationService {
    
    /**
     * Check if user has entitlement for a specific function
     */
    boolean hasEntitlement(String userId, String functionName);
    
    /**
     * Check if user has account access
     */
    boolean hasAccountAccess(String userId, String accountId);
    
    /**
     * Check if user has book access
     */
    boolean hasBookAccess(String userId, String bookId);
    
    /**
     * Check if user has security/instrument access
     */
    boolean hasSecurityAccess(String userId, String securityId);
    
    /**
     * Get user permissions
     */
    List<String> getUserPermissions(String userId);
    
    /**
     * Get user roles
     */
    List<String> getUserRoles(String userId);
    
    /**
     * Get user account IDs
     */
    List<String> getUserAccountIds(String userId);
    
    /**
     * Get user book IDs
     */
    List<String> getUserBookIds(String userId);
    
    /**
     * Get user context (cached or from IAM service)
     */
    UserContext getUserContext(String userId);
    
    /**
     * Batch check entitlements for multiple functions
     */
    java.util.Map<String, Boolean> batchCheckEntitlements(String userId, List<String> functionNames);
}
