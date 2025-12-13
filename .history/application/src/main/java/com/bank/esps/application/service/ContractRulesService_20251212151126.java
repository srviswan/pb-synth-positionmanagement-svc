package com.bank.esps.application.service;

import com.bank.esps.domain.cache.CacheService;
import com.bank.esps.domain.model.ContractRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for managing contract rules
 * In production, this would integrate with Contract Service
 * Uses cache abstraction (Redis, Memory, etc.) for storing rules
 */
@Service
public class ContractRulesService {
    
    private static final Logger log = LoggerFactory.getLogger(ContractRulesService.class);
    
    private static final String CACHE_KEY_PREFIX = "contract:rules:";
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(24);
    
    private final CacheService cacheService;
    private final Duration cacheTtl;
    
    public ContractRulesService(
            CacheService cacheService,
            @Value("${app.cache.default-ttl:PT24H}") Duration defaultTtl) {
        this.cacheService = cacheService;
        this.cacheTtl = defaultTtl;
    }
    
    /**
     * Get contract rules for a contract ID
     * Defaults to FIFO if not found
     */
    public ContractRules getContractRules(String contractId) {
        if (contractId == null || contractId.isEmpty()) {
            log.warn("Contract ID is null or empty, using default FIFO rules");
            return ContractRules.defaultRules("DEFAULT");
        }
        
        String cacheKey = CACHE_KEY_PREFIX + contractId;
        
        return cacheService.getOrCompute(
                cacheKey,
                ContractRules.class,
                () -> {
                    log.info("Contract rules not found for {}, using default FIFO rules", contractId);
                    return ContractRules.defaultRules(contractId);
                },
                cacheTtl
        );
    }
    
    /**
     * Update contract rules (called when contract events are received)
     */
    public void updateContractRules(ContractRules rules) {
        String cacheKey = CACHE_KEY_PREFIX + rules.getContractId();
        cacheService.put(cacheKey, rules, cacheTtl);
        log.info("Updated contract rules for contract: {}", rules.getContractId());
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
        String cacheKey = CACHE_KEY_PREFIX + contractId;
        cacheService.evict(cacheKey);
        log.info("Evicted contract rules from cache for contract: {}", contractId);
    }
}
