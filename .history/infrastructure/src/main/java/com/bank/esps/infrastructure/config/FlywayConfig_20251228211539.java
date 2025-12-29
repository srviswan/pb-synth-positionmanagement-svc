package com.bank.esps.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Flyway configuration for SQL Server migrations
 */
@Configuration
@EnableConfigurationProperties(FlywayProperties.class)
public class FlywayConfig {
    
    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);
    
    @Bean
    @Primary
    public Flyway flyway(DataSource dataSource, FlywayProperties flywayProperties) {
        log.info("🔷 Configuring Flyway for SQL Server migrations");
        
        FluentConfiguration config = Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(flywayProperties.isBaselineOnMigrate())
                .validateOnMigrate(flywayProperties.isValidateOnMigrate())
                .locations("classpath:db/migration/sqlserver");
        
        Flyway flyway = config.load();
        flyway.migrate();
        return flyway;
    }
}
