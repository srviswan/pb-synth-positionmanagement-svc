package com.bank.esps.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Flyway configuration that supports both PostgreSQL and MS SQL Server
 * Uses different migration directories based on database type
 */
@Configuration
@EnableConfigurationProperties(FlywayProperties.class)
public class FlywayConfig {
    
    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);
    
    @Value("${app.database.type:postgresql}")
    private String databaseType;
    
    @Bean
    @Primary
    public Flyway flyway(DataSource dataSource, FlywayProperties flywayProperties) {
        // Get database type from environment or app config
        String dbType = System.getenv("DB_TYPE");
        if (dbType == null || dbType.isEmpty()) {
            dbType = databaseType;
        }
        
        // Build Flyway configuration
        FluentConfiguration config = Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(flywayProperties.isBaselineOnMigrate())
                .validateOnMigrate(flywayProperties.isValidateOnMigrate());
        
        // Set migration locations based on database type
        if ("sqlserver".equalsIgnoreCase(dbType)) {
            log.info("🔷 Configuring Flyway for MS SQL Server migrations");
            config.locations("classpath:db/migration/sqlserver");
        } else {
            log.info("🐘 Configuring Flyway for PostgreSQL migrations");
            // Use default location or override from properties
            if (flywayProperties.getLocations() != null && !flywayProperties.getLocations().isEmpty()) {
                config.locations(flywayProperties.getLocations().toArray(new String[0]));
            } else {
                config.locations("classpath:db/migration");
            }
        }
        
        Flyway flyway = config.load();
        // Explicitly run migrations to ensure they run before Hibernate validation
        flyway.migrate();
        return flyway;
    }
}
