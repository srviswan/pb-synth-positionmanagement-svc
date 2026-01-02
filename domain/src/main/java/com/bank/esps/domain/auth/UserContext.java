package com.bank.esps.domain.auth;

import java.util.List;

/**
 * User context containing authentication and authorization information
 */
public class UserContext {
    private String userId;
    private String username;
    private String email;
    private List<String> roles;
    private List<String> permissions;
    private List<String> accountIds;  // Accounts user can access
    private List<String> bookIds;     // Books user can access
    private String sessionId;
    private String clientIp;
    
    public UserContext() {
    }
    
    public UserContext(String userId, String username, String email, 
                      List<String> roles, List<String> permissions,
                      List<String> accountIds, List<String> bookIds) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.roles = roles != null ? roles : java.util.Collections.emptyList();
        this.permissions = permissions != null ? permissions : java.util.Collections.emptyList();
        this.accountIds = accountIds != null ? accountIds : java.util.Collections.emptyList();
        this.bookIds = bookIds != null ? bookIds : java.util.Collections.emptyList();
    }
    
    // Getters and setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public List<String> getRoles() {
        return roles;
    }
    
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
    
    public List<String> getPermissions() {
        return permissions;
    }
    
    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
    
    public List<String> getAccountIds() {
        return accountIds;
    }
    
    public void setAccountIds(List<String> accountIds) {
        this.accountIds = accountIds;
    }
    
    public List<String> getBookIds() {
        return bookIds;
    }
    
    public void setBookIds(List<String> bookIds) {
        this.bookIds = bookIds;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
    
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
    
    public boolean hasAccountAccess(String accountId) {
        return accountIds != null && accountIds.contains(accountId);
    }
    
    public boolean hasBookAccess(String bookId) {
        return bookIds != null && bookIds.contains(bookId);
    }
}
