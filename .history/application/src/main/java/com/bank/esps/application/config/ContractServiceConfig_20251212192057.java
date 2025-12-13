package com.bank.esps.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

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
     * Configured with timeouts for resilience
     */
    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * Alternative RestTemplate with custom timeouts (if needed)
     */
    @Bean(name = "contractServiceRestTemplate")
    public RestTemplate contractServiceRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 seconds
        factory.setReadTimeout(10000);   // 10 seconds
        
        return new RestTemplate(factory);
    }
}
