package com.bank.esps.application.config;

import com.bank.esps.domain.contract.ContractService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for Contract Service integration
 * Supports switching between real Contract Service (REST) and mock implementation
 * 
 * Configuration via application.yml:
 *   app.contract.service.type: "rest" | "mock" (default: "mock")
 *   app.contract.service.url: "http://contract-service:8082/api/contracts" (for REST)
 *   app.contract.service.enabled: true | false (default: true)
 */
@Configuration
public class ContractServiceConfig {
    
    /**
     * RestTemplate bean for Contract Service REST client
     */
    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
