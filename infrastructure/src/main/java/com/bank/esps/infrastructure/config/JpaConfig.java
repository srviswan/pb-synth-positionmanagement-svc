package com.bank.esps.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * JPA/Hibernate configuration that supports both PostgreSQL and MS SQL Server
 * Sets the appropriate dialect based on database type
 */
@Configuration
public class JpaConfig {
    
    private static final Logger log = LoggerFactory.getLogger(JpaConfig.class);
    
    @Value("${app.database.type:postgresql}")
    private String databaseType;
    
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return (Map<String, Object> hibernateProperties) -> {
            if ("sqlserver".equalsIgnoreCase(databaseType)) {
                log.info("🔷 Configuring Hibernate for MS SQL Server");
                hibernateProperties.put("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect");
            } else {
                log.info("🐘 Configuring Hibernate for PostgreSQL");
                hibernateProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            }
        };
    }
}
