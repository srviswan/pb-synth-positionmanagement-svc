package com.bank.esps.api.filter;

import com.bank.esps.application.service.CorrelationIdService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to extract and set correlation ID from HTTP headers
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    
    private final CorrelationIdService correlationIdService;
    
    public CorrelationIdFilter(CorrelationIdService correlationIdService) {
        this.correlationIdService = correlationIdService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        try {
            // Extract correlation ID from header
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId != null && !correlationId.isEmpty()) {
                correlationIdService.setCorrelationId(correlationId);
            } else {
                // Generate new correlation ID if not present
                correlationId = correlationIdService.generateCorrelationId();
            }
            
            // Add correlation ID to response header
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            filterChain.doFilter(request, response);
        } finally {
            // Clean up MDC after request
            correlationIdService.clear();
        }
    }
}
