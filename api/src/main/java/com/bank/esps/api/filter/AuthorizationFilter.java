package com.bank.esps.api.filter;

import com.bank.esps.api.service.UserContextExtractor;
import com.bank.esps.application.service.authorization.EntitlementConfigurationService;
import com.bank.esps.domain.auth.AuthorizationService;
import com.bank.esps.domain.auth.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authorization filter that checks user entitlements before processing requests
 * Based on user_entitlements_architecture.md
 */
@Component
@Order(1) // Run before other filters
public class AuthorizationFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(AuthorizationFilter.class);
    
    private final AuthorizationService authorizationService;
    private final UserContextExtractor userContextExtractor;
    private final EntitlementConfigurationService entitlementConfigService;
    
    @Value("${app.authorization.enabled:true}")
    private boolean authorizationEnabled;
    
    @Value("${app.authorization.allow-anonymous:false}")
    private boolean allowAnonymous;
    
    public AuthorizationFilter(AuthorizationService authorizationService,
                              UserContextExtractor userContextExtractor,
                              EntitlementConfigurationService entitlementConfigService) {
        this.authorizationService = authorizationService;
        this.userContextExtractor = userContextExtractor;
        this.entitlementConfigService = entitlementConfigService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // Skip authorization for health checks and public endpoints
        String path = request.getRequestURI();
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        if (!authorizationEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extract user context
        UserContext userContext = userContextExtractor.extract(request);
        
        if (userContext == null || userContext.getUserId() == null) {
            if (allowAnonymous) {
                log.warn("No user context found, allowing anonymous access (development mode)");
                filterChain.doFilter(request, response);
                return;
            } else {
                log.warn("No user context found, denying access");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                return;
            }
        }
        
        // Determine required function from file-based configuration
        String requiredFunction = entitlementConfigService.findRequiredFunction(
            request.getMethod(), path
        );
        
        if (requiredFunction == null) {
            // No specific function required, allow access
            filterChain.doFilter(request, response);
            return;
        }
        
        // Check entitlement
        boolean authorized = authorizationService.hasEntitlement(
            userContext.getUserId(),
            requiredFunction
        );
        
        if (!authorized) {
            log.warn("User {} denied access to {} (required function: {})", 
                userContext.getUserId(), path, requiredFunction);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                "{\"error\":\"Forbidden\",\"message\":\"User does not have permission: %s\"}",
                requiredFunction
            ));
            return;
        }
        
        // Check data access if applicable (book)
        if (request.getMethod().equals("POST") || request.getMethod().equals("PUT")) {
            // For write operations, check book access from request body (done in controllers)
        }
        
        // Store user context in request attribute for use in controllers
        request.setAttribute("userContext", userContext);
        
        log.debug("User {} authorized for {} (function: {})", 
            userContext.getUserId(), path, requiredFunction);
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/health") || 
               path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs");
    }
}
