package com.bank.esps.application.service;

import com.bank.esps.domain.cache.CacheService;
import com.bank.esps.domain.contract.ContractService;
import com.bank.esps.domain.model.ContractRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for managing contract rules
 * Integrates with Contract Service (real or mock) and uses cache for performance
 * Uses cache abstraction (Redis, Memory, etc.) for storing rules
 */
@Service
public class ContractRulesService {
    
    private static final Logger log = LoggerFactory.getLogger(ContractRulesService.class);
    
    private static final String CACHE_KEY_PREFIX = "contract:rules:";
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(24);
    
    private final ContractService contractService;
    private final CacheService cacheService;
    private final Duration cacheTtl;
    private final boolean useCache;
    
    public ContractRulesService(
            ContractService contractService,
            CacheService cacheService,
            @Value("${app.cache.default-ttl:PT24H}") Duration defaultTtl,
            @Value("${app.contract.cache.enabled:true}") boolean useCache) {
        this.contractService = contractService;
        this.cacheService = cacheService;
        this.cacheTtl = defaultTtl;
        this.useCache = useCache;
    }
    
    /**
     * Get contract rules for a contract ID
     * First checks cache, then calls Contract Service if not cached
     * Defaults to FIFO if not found
     */
    public ContractRules getContractRules(String contractId) {
        if (contractId == null || contractId.isEmpty()) {
            log.warn("Contract ID is null or empty, using default FIFO rules");
            return ContractRules.defaultRules("DEFAULT");
        }
        
        String cacheKey = CACHE_KEY_PREFIX + contractId;
        
        // If cache is enabled, check cache first
        if (useCache) {
            java.util.Optional<ContractRules> cachedRulesOpt = cacheService.get(cacheKey, ContractRules.class);
            if (cachedRulesOpt.isPresent()) {
                ContractRules cachedRules = cachedRulesOpt.get();
                log.debug("Retrieved contract rules from cache for contract: {}", contractId);
                return cachedRules;
            }
        }
        
        // Cache miss or cache disabled - fetch from Contract Service
        log.debug("Fetching contract rules from Contract Service for contract: {}", contractId);
        ContractRules rules = contractService.getContractRulesSync(contractId);
        
        // Cache the result if cache is enabled
        if (useCache && rules != null) {
            cacheService.put(cacheKey, rules, cacheTtl);
            log.debug("Cached contract rules for contract: {}", contractId);
        }
        
        return rules;
    }
    
    /**
     * Update contract rules (called when contract events are received)
     * Updates both Contract Service and cache
     */
    public void updateContractRules(ContractRules rules) {
        String contractId = rules.getContractId();
        
        // Update Contract Service asynchronously
        contractService.updateContractRules(contractId, rules)
                .thenRun(() -> log.info("Updated contract rules in Contract Service for contract: {}", contractId))
                .exceptionally(ex -> {
                    log.error("Error updating contract rules in Contract Service for contract {}: {}", 
                            contractId, ex.getMessage());
                    return null;
                });
        
        // Update cache synchronously
        if (useCache) {
            String cacheKey = CACHE_KEY_PREFIX + contractId;
            cacheService.put(cacheKey, rules, cacheTtl);
            log.info("Updated contract rules in cache for contract: {}", contractId);
        }
    }
    
    /**
     * Clear cache (for testing or cache refresh)
     */
    public void clearCache() {
        // Note: This clears entire cache. For production, consider evicting specific keys
        cacheService.clear();
        log.info("Cleared contract rules cache");
    }
    
    /**
     * Evict specific contract rules from cache
     */
    public void evictContractRules(String contractId) {
        if (useCache) {
            String cacheKey = CACHE_KEY_PREFIX + contractId;
            cacheService.evict(cacheKey);
            log.info("Evicted contract rules from cache for contract: {}", contractId);
        }
    }
    
    /**
     * Check if Contract Service is available
     */
    public boolean isContractServiceAvailable() {
        return contractService.isAvailable();
    }
}
