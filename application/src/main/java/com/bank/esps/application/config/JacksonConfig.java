package com.bank.esps.application.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Centralized Jackson ObjectMapper configuration
 * Ensures consistent JSON serialization/deserialization across the application
 */
@Configuration
public class JacksonConfig {
    
    /**
     * Primary ObjectMapper bean configured for the application
     * - Ignores unknown properties (for schema evolution)
     * - Handles Java 8 time types
     * - Writes dates as ISO-8601 strings
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // CRITICAL: Disable FAIL_ON_UNKNOWN_PROPERTIES to support schema evolution
        // This allows deserializing older events that may have additional fields
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        
        // Register Java 8 time module for LocalDate, OffsetDateTime, etc.
        mapper.registerModule(new JavaTimeModule());
        
        // Write dates as ISO-8601 strings instead of timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        return mapper;
    }
}
