package com.bank.esps.infrastructure.contract;

import com.bank.esps.domain.contract.ContractService;
import com.bank.esps.domain.model.Contract;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Real implementation of ContractService that calls external Contract Service via REST API
 * 
 * Features:
 * - Circuit breaker integration for resilience
 * - Timeout handling
 * - Graceful fallback to default rules on errors
 * - Health check support
 * 
 * Configuration:
 * - app.contract.service.type: "rest"
 * - app.contract.service.url: "http://contract-service:8082/api/contracts"
 * - app.contract.service.enabled: true
 */
@Component
@ConditionalOnProperty(
        name = "app.contract.service.type",
        havingValue = "rest",
        matchIfMissing = false
)
public class ContractServiceClient implements ContractService {
    
    private static final Logger log = LoggerFactory.getLogger(ContractServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String contractServiceUrl;
    private final boolean enabled;
    private final CircuitBreaker circuitBreaker;
    
    public ContractServiceClient(
            @Qualifier("contractServiceRestTemplate") RestTemplate restTemplate,
            @Value("${app.contract.service.url:http://localhost:8082/api/contracts}") String contractServiceUrl,
            @Value("${app.contract.service.enabled:true}") boolean enabled,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.restTemplate = restTemplate;
        this.contractServiceUrl = contractServiceUrl;
        this.enabled = enabled;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("contractService");
    }
    
    @Override
    public CompletableFuture<Contract> getContract(String contractId) {
        return CompletableFuture.supplyAsync(() -> getContractSync(contractId));
    }
    
    @Override
    public Contract getContractSync(String contractId) {
        if (!enabled) {
            log.debug("Contract service is disabled, returning default contract for contract: {}", contractId);
            return Contract.defaultContract(contractId);
        }
        
        if (contractId == null || contractId.isEmpty()) {
            log.warn("Contract ID is null or empty, using default FIFO contract");
            return Contract.defaultContract("DEFAULT");
        }
        
        // Use circuit breaker for resilience
        Supplier<Contract> supplier = () -> {
            try {
                String url = contractServiceUrl + "/" + contractId;
                log.debug("Fetching contract from Contract Service: {}", url);
                
                ResponseEntity<Contract> response = restTemplate.getForEntity(url, Contract.class);
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Contract contract = response.getBody();
                    log.info("Retrieved contract for contract {}: method={}", 
                            contractId, contract.getTaxLotMethod());
                    return contract;
                } else {
                    log.warn("Contract Service returned non-OK status for contract {}: {}", 
                            contractId, response.getStatusCode());
                    return Contract.defaultContract(contractId);
                }
                
            } catch (ResourceAccessException e) {
                // Timeout or connection error
                log.error("Timeout or connection error calling Contract Service for contract {}: {}", 
                        contractId, e.getMessage());
                throw new RuntimeException("Contract Service unavailable", e);
            } catch (RestClientException e) {
                // Other REST client errors
                log.error("Error calling Contract Service for contract {}: {}", contractId, e.getMessage());
                throw new RuntimeException("Contract Service error", e);
            } catch (Exception e) {
                log.error("Unexpected error calling Contract Service for contract {}: {}", 
                        contractId, e.getMessage(), e);
                throw new RuntimeException("Unexpected Contract Service error", e);
            }
        };
        
        // Execute with circuit breaker, fallback to default contract on failure
        try {
            return circuitBreaker.executeSupplier(supplier);
        } catch (Exception e) {
            log.warn("Circuit breaker or error occurred, falling back to default contract for contract {}: {}", 
                    contractId, e.getMessage());
            return Contract.defaultContract(contractId);
        }
    }
    
    @Override
    public CompletableFuture<Void> updateContract(String contractId, Contract contract) {
        return CompletableFuture.runAsync(() -> {
            if (!enabled) {
                log.debug("Contract service is disabled, skipping update for contract: {}", contractId);
                return;
            }
            
            Supplier<Void> supplier = () -> {
                try {
                    String url = contractServiceUrl + "/" + contractId;
                    log.debug("Updating contract in Contract Service: {}", url);
                    
                    restTemplate.put(url, contract);
                    log.info("Updated contract in Contract Service for contract: {}", contractId);
                    return null;
                    
                } catch (ResourceAccessException e) {
                    log.error("Timeout or connection error updating contract for contract {}: {}", 
                            contractId, e.getMessage());
                    throw new RuntimeException("Contract Service unavailable", e);
                } catch (RestClientException e) {
                    log.error("Error updating contract in Contract Service for contract {}: {}", 
                            contractId, e.getMessage());
                    throw new RuntimeException("Contract Service error", e);
                } catch (Exception e) {
                    log.error("Unexpected error updating contract in Contract Service for contract {}: {}", 
                            contractId, e.getMessage(), e);
                    throw new RuntimeException("Unexpected Contract Service error", e);
                }
            };
            
            // Execute with circuit breaker (non-blocking, errors are logged but don't fail)
            try {
                circuitBreaker.executeSupplier(supplier);
            } catch (Exception e) {
                log.warn("Failed to update contract in Contract Service for contract {}: {}", 
                        contractId, e.getMessage());
                // Don't throw - update failures shouldn't block position processing
            }
        });
    }
    
    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        
        try {
            String healthUrl = contractServiceUrl.replace("/api/contracts", "/health");
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.debug("Contract Service health check failed: {}", e.getMessage());
            return false;
        }
    }
}
