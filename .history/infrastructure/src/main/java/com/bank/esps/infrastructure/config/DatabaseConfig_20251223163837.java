package com.bank.esps.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Database configuration that supports both PostgreSQL and MS SQL Server
 * Configurable via application.yml or environment variables
 * 
 * Set DB_TYPE=sqlserver or DB_TYPE=postgresql to switch databases
 */
@Configuration
public class DatabaseConfig {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    
    @Value("${app.database.type:postgresql}")
    private String databaseType;
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        
        // Set default values based on database type
        if ("sqlserver".equalsIgnoreCase(databaseType)) {
            log.info("🔷 Configuring MS SQL Server database");
            
            // Default MS SQL Server connection if not provided
            String host = System.getenv().getOrDefault("DB_HOST", "localhost");
            String port = System.getenv().getOrDefault("DB_PORT", "1433");
            String database = System.getenv().getOrDefault("DB_NAME", "equity_swap_db");
            String url = System.getenv("DB_URL");
            
            if (url == null || url.isEmpty()) {
                url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true", 
                        host, port, database);
            }
            properties.setUrl(url);
            
            if (properties.getDriverClassName() == null || properties.getDriverClassName().isEmpty()) {
                properties.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            }
        } else {
            log.info("🐘 Configuring PostgreSQL database");
            
            // Default PostgreSQL connection if not provided
            String host = System.getenv().getOrDefault("DB_HOST", "localhost");
            String port = System.getenv().getOrDefault("DB_PORT", "5432");
            String database = System.getenv().getOrDefault("DB_NAME", "equity_swap_db");
            String url = System.getenv("DB_URL");
            
            if (url == null || url.isEmpty()) {
                url = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);
            }
            properties.setUrl(url);
            
            if (properties.getDriverClassName() == null || properties.getDriverClassName().isEmpty()) {
                properties.setDriverClassName("org.postgresql.Driver");
            }
        }
        
        return properties;
    }
    
    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
