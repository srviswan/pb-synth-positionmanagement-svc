package com.bank.esps.api.filter;

import com.bank.esps.api.service.UserContextExtractor;
import com.bank.esps.domain.auth.AuthorizationService;
import com.bank.esps.domain.auth.PositionFunction;
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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

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
    
    @Value("${app.authorization.enabled:true}")
    private boolean authorizationEnabled;
    
    @Value("${app.authorization.allow-anonymous:false}")
    private boolean allowAnonymous;
    
    // Map of URL patterns to required functions
    private static final Map<Pattern, PositionFunction> URL_FUNCTION_MAP = new HashMap<>();
    
    static {
        // Trade endpoints
        URL_FUNCTION_MAP.put(Pattern.compile("/api/trades"), PositionFunction.TRADE_CREATE);
        URL_FUNCTION_MAP.put(Pattern.compile("/api/trades/.*"), PositionFunction.TRADE_VIEW);
        
        // Position endpoints
        URL_FUNCTION_MAP.put(Pattern.compile("GET /api/positions"), PositionFunction.POSITION_VIEW);
        URL_FUNCTION_MAP.put(Pattern.compile("GET /api/positions/.*"), PositionFunction.POSITION_VIEW);
        URL_FUNCTION_MAP.put(Pattern.compile("PUT /api/positions/.*"), PositionFunction.POSITION_UPDATE);
        
        // Diagnostics endpoints
        URL_FUNCTION_MAP.put(Pattern.compile("/api/diagnostics"), PositionFunction.DIAGNOSTICS_VIEW);
        URL_FUNCTION_MAP.put(Pattern.compile("/api/diagnostics/recalculate"), PositionFunction.DIAGNOSTICS_RECALCULATE);
        URL_FUNCTION_MAP.put(Pattern.compile("/api/diagnostics/recalculate/async"), PositionFunction.DIAGNOSTICS_RECALCULATE);
    }
    
    public AuthorizationFilter(AuthorizationService authorizationService,
                              UserContextExtractor userContextExtractor) {
        this.authorizationService = authorizationService;
        this.userContextExtractor = userContextExtractor;
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
        
        // Determine required function
        PositionFunction requiredFunction = determineRequiredFunction(request);
        
        if (requiredFunction == null) {
            // No specific function required, allow access
            filterChain.doFilter(request, response);
            return;
        }
        
        // Check entitlement
        boolean authorized = authorizationService.hasEntitlement(
            userContext.getUserId(),
            requiredFunction.getFunctionName()
        );
        
        if (!authorized) {
            log.warn("User {} denied access to {} (required function: {})", 
                userContext.getUserId(), path, requiredFunction.getFunctionName());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                "{\"error\":\"Forbidden\",\"message\":\"User does not have permission: %s\"}",
                requiredFunction.getFunctionName()
            ));
            return;
        }
        
        // Check data access if applicable (account, book)
        if (request.getMethod().equals("POST") || request.getMethod().equals("PUT")) {
            // For write operations, check account access from request body
            // This will be checked again in the controller
        }
        
        // Store user context in request attribute for use in controllers
        request.setAttribute("userContext", userContext);
        
        log.debug("User {} authorized for {} (function: {})", 
            userContext.getUserId(), path, requiredFunction.getFunctionName());
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/health") || 
               path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs");
    }
    
    private PositionFunction determineRequiredFunction(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String methodPath = method + " " + path;
        
        // Check method-specific patterns first
        for (Map.Entry<Pattern, PositionFunction> entry : URL_FUNCTION_MAP.entrySet()) {
            if (entry.getKey().matcher(methodPath).matches() || 
                entry.getKey().matcher(path).matches()) {
                return entry.getValue();
            }
        }
        
        // Default mappings based on path
        if (path.startsWith("/api/trades")) {
            if ("POST".equals(method)) {
                return PositionFunction.TRADE_CREATE;
            } else {
                return PositionFunction.TRADE_VIEW;
            }
        } else if (path.startsWith("/api/positions")) {
            if ("GET".equals(method)) {
                return PositionFunction.POSITION_VIEW;
            } else if ("PUT".equals(method) || "PATCH".equals(method)) {
                return PositionFunction.POSITION_UPDATE;
            }
        } else if (path.startsWith("/api/diagnostics/recalculate")) {
            return PositionFunction.DIAGNOSTICS_RECALCULATE;
        } else if (path.startsWith("/api/diagnostics")) {
            return PositionFunction.DIAGNOSTICS_VIEW;
        }
        
        return null; // No specific function required
    }
}
