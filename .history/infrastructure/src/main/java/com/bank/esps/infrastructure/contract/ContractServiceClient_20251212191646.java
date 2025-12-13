package com.bank.esps.infrastructure.contract;

import com.bank.esps.domain.contract.ContractService;
import com.bank.esps.domain.model.ContractRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * Real implementation of ContractService that calls external Contract Service
 * Uses REST API to fetch contract rules
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
    
    public ContractServiceClient(
            RestTemplate restTemplate,
            @Value("${app.contract.service.url:http://localhost:8082/api/contracts}") String contractServiceUrl,
            @Value("${app.contract.service.enabled:true}") boolean enabled) {
        this.restTemplate = restTemplate;
        this.contractServiceUrl = contractServiceUrl;
        this.enabled = enabled;
    }
    
    @Override
    public CompletableFuture<ContractRules> getContractRules(String contractId) {
        return CompletableFuture.supplyAsync(() -> getContractRulesSync(contractId));
    }
    
    @Override
    public ContractRules getContractRulesSync(String contractId) {
        if (!enabled) {
            log.debug("Contract service is disabled, returning default rules for contract: {}", contractId);
            return ContractRules.defaultRules(contractId);
        }
        
        if (contractId == null || contractId.isEmpty()) {
            log.warn("Contract ID is null or empty, using default FIFO rules");
            return ContractRules.defaultRules("DEFAULT");
        }
        
        try {
            String url = contractServiceUrl + "/" + contractId + "/rules";
            log.debug("Fetching contract rules from Contract Service: {}", url);
            
            ResponseEntity<ContractRules> response = restTemplate.getForEntity(url, ContractRules.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ContractRules rules = response.getBody();
                log.info("Retrieved contract rules for contract {}: method={}", 
                        contractId, rules.getTaxLotMethod());
                return rules;
            } else {
                log.warn("Contract Service returned non-OK status for contract {}: {}", 
                        contractId, response.getStatusCode());
                return ContractRules.defaultRules(contractId);
            }
            
        } catch (RestClientException e) {
            log.error("Error calling Contract Service for contract {}: {}", contractId, e.getMessage());
            // Fallback to default rules on error
            return ContractRules.defaultRules(contractId);
        } catch (Exception e) {
            log.error("Unexpected error calling Contract Service for contract {}: {}", contractId, e.getMessage(), e);
            return ContractRules.defaultRules(contractId);
        }
    }
    
    @Override
    public CompletableFuture<Void> updateContractRules(String contractId, ContractRules rules) {
        return CompletableFuture.runAsync(() -> {
            if (!enabled) {
                log.debug("Contract service is disabled, skipping update for contract: {}", contractId);
                return;
            }
            
            try {
                String url = contractServiceUrl + "/" + contractId + "/rules";
                log.debug("Updating contract rules in Contract Service: {}", url);
                
                restTemplate.put(url, rules);
                log.info("Updated contract rules in Contract Service for contract: {}", contractId);
                
            } catch (RestClientException e) {
                log.error("Error updating contract rules in Contract Service for contract {}: {}", 
                        contractId, e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error updating contract rules in Contract Service for contract {}: {}", 
                        contractId, e.getMessage(), e);
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
