package com.bank.esps.application.service.authorization;

import com.bank.esps.domain.auth.AuthorizationService;
import com.bank.esps.domain.auth.UserContext;
import com.bank.esps.domain.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Default implementation of AuthorizationService
 * Integrates with IAM service for authorization decisions
 * Includes caching for performance optimization
 */
@Service
public class DefaultAuthorizationService implements AuthorizationService {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultAuthorizationService.class);
    
    private final RestTemplate restTemplate;
    private final CacheService cacheService;
    
    @Value("${app.iam.service.url:http://localhost:8081}")
    private String iamServiceUrl;
    
    @Value("${app.iam.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${app.iam.cache.ttl.minutes:5}")
    private int cacheTtlMinutes;
    
    public DefaultAuthorizationService(RestTemplate restTemplate, CacheService cacheService) {
        this.restTemplate = restTemplate;
        this.cacheService = cacheService;
    }
    
    @Override
    public boolean hasEntitlement(String userId, String functionName) {
        if (userId == null || functionName == null) {
            log.warn("hasEntitlement called with null userId or functionName");
            return false;
        }
        
        String cacheKey = "auth:entitlement:" + userId + ":" + functionName;
        
        // Check cache first
        if (cacheEnabled) {
            Optional<Boolean> cached = cacheService.get(cacheKey, Boolean.class);
            if (cached.isPresent()) {
                log.debug("Cache hit for entitlement check: userId={}, function={}", userId, functionName);
                Boolean result = cached.get();
                if (result != null) {
                    return result;
                }
            }
        }
        
        // Check with IAM service
        boolean hasEntitlement = checkWithIamService(userId, functionName);
        
        // Cache result
        if (cacheEnabled) {
            cacheService.put(cacheKey, hasEntitlement, cacheTtlMinutes, java.util.concurrent.TimeUnit.MINUTES);
        }
        
        return hasEntitlement;
    }
    
    private boolean checkWithIamService(String userId, String functionName) {
        try {
            // In a real implementation, this would call the IAM service
            // For now, we'll use a mock implementation that allows all requests
            // TODO: Replace with actual IAM service call
            String url = iamServiceUrl + "/api/v1/authorize?userId=" + userId + "&function=" + functionName;
            
            // Mock: For development, allow all if IAM service is not available
            try {
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                if (response != null && response.containsKey("authorized")) {
                    return Boolean.TRUE.equals(response.get("authorized"));
                }
            } catch (Exception e) {
                log.warn("IAM service unavailable, using default authorization (allowing all): {}", e.getMessage());
                // In development, allow all if IAM service is not available
                // In production, this should fail closed (return false)
                return true; // TODO: Change to false in production
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error checking entitlement with IAM service", e);
            // Fail closed in production, but allow in development
            return false;
        }
    }
    
    @Override
    public boolean hasAccountAccess(String userId, String accountId) {
        if (userId == null || accountId == null) {
            return false;
        }
        
        String cacheKey = "auth:account:" + userId + ":" + accountId;
        
        if (cacheEnabled) {
            Optional<Boolean> cached = cacheService.get(cacheKey, Boolean.class);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        
        // Check with IAM service
        try {
            String url = iamServiceUrl + "/api/v1/authorize/account?userId=" + userId + "&accountId=" + accountId;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            boolean hasAccess = response != null && Boolean.TRUE.equals(response.get("authorized"));
            
            if (cacheEnabled) {
                cacheService.put(cacheKey, hasAccess, cacheTtlMinutes, java.util.concurrent.TimeUnit.MINUTES);
            }
            
            return hasAccess;
        } catch (Exception e) {
            log.warn("IAM service unavailable for account access check, allowing: {}", e.getMessage());
            return true; // TODO: Change to false in production
        }
    }
    
    @Override
    public boolean hasBookAccess(String userId, String bookId) {
        if (userId == null || bookId == null) {
            return false;
        }
        
        // Similar to account access
        try {
            String url = iamServiceUrl + "/api/v1/authorize/book?userId=" + userId + "&bookId=" + bookId;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null && Boolean.TRUE.equals(response.get("authorized"));
        } catch (Exception e) {
            log.warn("IAM service unavailable for book access check, allowing: {}", e.getMessage());
            return true; // TODO: Change to false in production
        }
    }
    
    @Override
    public boolean hasSecurityAccess(String userId, String securityId) {
        // Similar implementation
        return true; // TODO: Implement with IAM service
    }
    
    @Override
    public List<String> getUserPermissions(String userId) {
        String cacheKey = "auth:permissions:" + userId;
        
        if (cacheEnabled) {
            @SuppressWarnings("unchecked")
            Optional<List<String>> cached = (Optional<List<String>>) (Optional<?>) cacheService.get(cacheKey, List.class);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        
        try {
            String url = iamServiceUrl + "/api/v1/users/" + userId + "/permissions";
            @SuppressWarnings("unchecked")
            List<String> permissions = (List<String>) restTemplate.getForObject(url, List.class);
            if (permissions == null) {
                permissions = Collections.emptyList();
            }
            
            if (cacheEnabled) {
                cacheService.put(cacheKey, permissions, cacheTtlMinutes, java.util.concurrent.TimeUnit.MINUTES);
            }
            
            return permissions;
        } catch (Exception e) {
            log.warn("IAM service unavailable, returning empty permissions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<String> getUserRoles(String userId) {
        String cacheKey = "auth:roles:" + userId;
        
        if (cacheEnabled) {
            @SuppressWarnings("unchecked")
            Optional<List<String>> cached = (Optional<List<String>>) (Optional<?>) cacheService.get(cacheKey, List.class);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        
        try {
            String url = iamServiceUrl + "/api/v1/users/" + userId + "/roles";
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) restTemplate.getForObject(url, List.class);
            if (roles == null) {
                roles = Collections.emptyList();
            }
            
            if (cacheEnabled) {
                cacheService.put(cacheKey, roles, cacheTtlMinutes, java.util.concurrent.TimeUnit.MINUTES);
            }
            
            return roles;
        } catch (Exception e) {
            log.warn("IAM service unavailable, returning empty roles: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<String> getUserAccountIds(String userId) {
        try {
            String url = iamServiceUrl + "/api/v1/users/" + userId + "/accounts";
            List<String> accounts = restTemplate.getForObject(url, List.class);
            return accounts != null ? accounts : Collections.emptyList();
        } catch (Exception e) {
            log.warn("IAM service unavailable, returning empty accounts: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<String> getUserBookIds(String userId) {
        try {
            String url = iamServiceUrl + "/api/v1/users/" + userId + "/books";
            List<String> books = restTemplate.getForObject(url, List.class);
            return books != null ? books : Collections.emptyList();
        } catch (Exception e) {
            log.warn("IAM service unavailable, returning empty books: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    @Override
    public UserContext getUserContext(String userId) {
        String cacheKey = "auth:context:" + userId;
        
        if (cacheEnabled) {
            Optional<UserContext> cached = cacheService.get(cacheKey, UserContext.class);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        
        try {
            String url = iamServiceUrl + "/api/v1/users/" + userId + "/context";
            UserContext context = restTemplate.getForObject(url, UserContext.class);
            
            if (context != null && cacheEnabled) {
                cacheService.put(cacheKey, context, cacheTtlMinutes, java.util.concurrent.TimeUnit.MINUTES);
            }
            
            return context;
        } catch (Exception e) {
            log.warn("IAM service unavailable, creating default context: {}", e.getMessage());
            // Return a default context for development
            return new UserContext(userId, userId, null, 
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        }
    }
    
    @Override
    public Map<String, Boolean> batchCheckEntitlements(String userId, List<String> functionNames) {
        Map<String, Boolean> results = new HashMap<>();
        
        for (String functionName : functionNames) {
            results.put(functionName, hasEntitlement(userId, functionName));
        }
        
        return results;
    }
    
    /**
     * Invalidate cache for a user (call when permissions change)
     */
    public void invalidateUserCache(String userId) {
        // In a real implementation, would use pattern matching to delete all keys
        // For now, we'll rely on TTL expiration
        log.info("Cache invalidation requested for user: {}", userId);
    }
}
