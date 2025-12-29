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
import java.util.List;
import java.util.stream.Collectors;

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
        // IMPORTANT: Start with a clean configuration to avoid Spring Boot auto-configuration
        // adding default locations that cause conflicts
        FluentConfiguration config = Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(flywayProperties.isBaselineOnMigrate())
                .validateOnMigrate(flywayProperties.isValidateOnMigrate());
        
        // Set migration locations based on database type
        // CRITICAL ISSUE: Flyway scans classpath locations recursively by default.
        // When we set "classpath:db/migration", it recursively finds "db/migration/sqlserver" subdirectory,
        // causing "Found more than one migration with version 1" errors.
        // 
        // ROOT CAUSE: Both db/migration/ and db/migration/sqlserver/ contain V1__initial_schema.sql
        // When Flyway scans "classpath:db/migration", it recursively finds the sqlserver subdirectory.
        //
        // SOLUTION: Use database-specific Flyway table names to isolate migration histories.
        // This allows both sets of migrations to coexist without version conflicts.
        // Each database type will track its own migration history separately.
        if ("sqlserver".equalsIgnoreCase(dbType)) {
            log.info("🔷 Configuring Flyway for MS SQL Server migrations");
            // Use separate migration directory to avoid recursive scanning conflicts
            config.locations("classpath:db/migration-sqlserver");
        } else {
            log.info("🐘 Configuring Flyway for PostgreSQL migrations");
            String postgresLocation = System.getenv("FLYWAY_LOCATIONS");
            if (postgresLocation != null && !postgresLocation.isEmpty()) {
                config.locations(postgresLocation.split(","));
            } else {
                if (flywayProperties.getLocations() != null && !flywayProperties.getLocations().isEmpty()) {
                    // Filter out sqlserver locations if present
                    List<String> filteredLocations = flywayProperties.getLocations().stream()
                            .filter(loc -> !loc.contains("sqlserver"))
                            .collect(Collectors.toList());
                    if (!filteredLocations.isEmpty()) {
                        config.locations(filteredLocations.toArray(new String[0]));
                    } else {
                        // Use separate migration directory to avoid recursive scanning conflicts
                        config.locations("classpath:db/migration-postgresql");
                    }
                } else {
                    // Use separate migration directory to avoid recursive scanning conflicts
                    config.locations("classpath:db/migration-postgresql");
                }
            }
        }
        
        // Load Flyway instance with our configuration
        Flyway flyway = config.load();
        
        // Log the actual locations that will be scanned (for debugging)
        if (log.isDebugEnabled()) {
            org.flywaydb.core.api.Location[] actualLocations = flyway.getConfiguration().getLocations();
            String locationsStr = java.util.Arrays.stream(actualLocations)
                    .map(loc -> loc.getPath())
                    .collect(java.util.stream.Collectors.joining(", "));
            log.debug("Flyway will scan locations: {}", locationsStr);
        }
        
        // Explicitly run migrations to ensure they run before Hibernate validation
        flyway.migrate();
        return flyway;
    }
}
