package com.bank.esps.domain.auth;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entitlement metadata attached to events and operations
 */
public class EntitlementMetadata {
    private String requiredFunction;      // e.g., "trade:create", "position:read"
    private String requiredPermission;    // e.g., "trade:create"
    private List<String> requiredRoles;   // e.g., ["TRADE_CAPTURE_USER", "POSITION_VIEWER"]
    private String authorizationStatus;    // AUTHORIZED, UNAUTHORIZED, PENDING
    private LocalDateTime authorizedAt;
    private String authorizedBy;          // System or user ID
    private String signature;             // Optional: event signature for verification
    
    public EntitlementMetadata() {
    }
    
    public EntitlementMetadata(String requiredFunction) {
        this.requiredFunction = requiredFunction;
        this.authorizationStatus = "PENDING";
        this.authorizedAt = LocalDateTime.now();
    }
    
    // Getters and setters
    public String getRequiredFunction() {
        return requiredFunction;
    }
    
    public void setRequiredFunction(String requiredFunction) {
        this.requiredFunction = requiredFunction;
    }
    
    public String getRequiredPermission() {
        return requiredPermission;
    }
    
    public void setRequiredPermission(String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }
    
    public List<String> getRequiredRoles() {
        return requiredRoles;
    }
    
    public void setRequiredRoles(List<String> requiredRoles) {
        this.requiredRoles = requiredRoles;
    }
    
    public String getAuthorizationStatus() {
        return authorizationStatus;
    }
    
    public void setAuthorizationStatus(String authorizationStatus) {
        this.authorizationStatus = authorizationStatus;
    }
    
    public LocalDateTime getAuthorizedAt() {
        return authorizedAt;
    }
    
    public void setAuthorizedAt(LocalDateTime authorizedAt) {
        this.authorizedAt = authorizedAt;
    }
    
    public String getAuthorizedBy() {
        return authorizedBy;
    }
    
    public void setAuthorizedBy(String authorizedBy) {
        this.authorizedBy = authorizedBy;
    }
    
    public String getSignature() {
        return signature;
    }
    
    public void setSignature(String signature) {
        this.signature = signature;
    }
    
    public boolean isAuthorized() {
        return "AUTHORIZED".equals(authorizationStatus);
    }
}
