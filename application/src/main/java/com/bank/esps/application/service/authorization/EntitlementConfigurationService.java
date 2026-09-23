package com.bank.esps.application.service.authorization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for loading entitlements from configuration file
 * Supports YAML-based entitlement definitions and URL mappings
 */
@Service
@Configuration
public class EntitlementConfigurationService {
    
    private static final Logger log = LoggerFactory.getLogger(EntitlementConfigurationService.class);
    
    @Value("${app.authorization.entitlements.file:entitlements.yml}")
    private String entitlementsFile;
    
    private final ResourceLoader resourceLoader;
    
    private Map<String, String> entitlements = new HashMap<>();
    private List<UrlMapping> urlMappings = new ArrayList<>();
    
    public EntitlementConfigurationService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    
    @PostConstruct
    public void loadEntitlements() {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + entitlementsFile);
            
            if (!resource.exists()) {
                log.warn("Entitlements file not found: {}, using defaults", entitlementsFile);
                loadDefaultEntitlements();
                return;
            }
            
            Yaml yaml = new Yaml();
            try (InputStream inputStream = resource.getInputStream()) {
                Map<String, Object> data = yaml.load(inputStream);
                
                // Load entitlements
                if (data.containsKey("entitlements")) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> ents = (Map<String, String>) data.get("entitlements");
                    if (ents != null) {
                        this.entitlements = ents;
                        log.info("Loaded {} entitlements from file", entitlements.size());
                    }
                }
                
                // Load URL mappings
                if (data.containsKey("urlMappings")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> mappings = (List<Map<String, Object>>) data.get("urlMappings");
                    if (mappings != null) {
                        this.urlMappings = mappings.stream()
                            .map(m -> new UrlMapping(
                                (String) m.get("method"),
                                (String) m.get("pattern"),
                                (String) m.get("function")
                            ))
                            .collect(Collectors.toList());
                        log.info("Loaded {} URL mappings from file", urlMappings.size());
                    }
                }
            }
            
            log.info("Successfully loaded entitlements configuration from {}", entitlementsFile);
            
        } catch (Exception e) {
            log.error("Error loading entitlements from file: {}", entitlementsFile, e);
            log.warn("Falling back to default entitlements");
            loadDefaultEntitlements();
        }
    }
    
    private void loadDefaultEntitlements() {
        // Default entitlements if file loading fails
        entitlements.put("position:view", "View positions");
        entitlements.put("position:search", "Search positions");
        entitlements.put("position:export", "Export position data");
        entitlements.put("position:update", "Update positions");
        entitlements.put("position:adjust", "Adjust positions");
        entitlements.put("trade:create", "Create new trades");
        entitlements.put("trade:view", "View trades");
        entitlements.put("trade:increase", "Increase trade quantity");
        entitlements.put("trade:decrease", "Decrease trade quantity");
        entitlements.put("trade:terminate", "Terminate trades");
        entitlements.put("diagnostics:view", "View diagnostics");
        entitlements.put("diagnostics:recalculate", "Trigger recalculation");
        entitlements.put("diagnostics:admin", "Diagnostics administration");
        entitlements.put("position:admin", "Position administration");
        
        log.info("Loaded {} default entitlements", entitlements.size());
    }
    
    /**
     * Get all entitlements
     */
    public Map<String, String> getAllEntitlements() {
        return Collections.unmodifiableMap(entitlements);
    }
    
    /**
     * Check if an entitlement exists
     */
    public boolean hasEntitlement(String functionName) {
        return entitlements.containsKey(functionName);
    }
    
    /**
     * Get description for an entitlement
     */
    public String getEntitlementDescription(String functionName) {
        return entitlements.get(functionName);
    }
    
    /**
     * Find required function for a URL pattern and HTTP method
     */
    public String findRequiredFunction(String method, String path) {
        String methodPath = method + " " + path;
        
        for (UrlMapping mapping : urlMappings) {
            if (mapping.matches(method, path)) {
                return mapping.getFunction();
            }
        }
        
        return null;
    }
    
    /**
     * Get all URL mappings
     */
    public List<UrlMapping> getUrlMappings() {
        return Collections.unmodifiableList(urlMappings);
    }
    
    /**
     * URL mapping configuration
     */
    public static class UrlMapping {
        private final String method;
        private final Pattern pattern;
        private final String function;
        
        public UrlMapping(String method, String pattern, String function) {
            this.method = method;
            this.pattern = Pattern.compile(pattern);
            this.function = function;
        }
        
        public boolean matches(String method, String path) {
            if (!this.method.equals(method)) {
                return false;
            }
            return pattern.matcher(path).matches();
        }
        
        public String getMethod() {
            return method;
        }
        
        public String getFunction() {
            return function;
        }
    }
}
