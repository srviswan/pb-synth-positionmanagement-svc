package com.bank.esps.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Database configuration for SQL Server
 */
@Configuration
public class DatabaseConfig {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    
    @Value("${DB_HOST:localhost}")
    private String dbHost;
    
    @Value("${DB_PORT:1433}")
    private String dbPort;
    
    @Value("${DB_NAME:equity_swap_db}")
    private String dbName;
    
    @Value("${DB_USERNAME:SA}")
    private String dbUsername;
    
    @Value("${DB_PASSWORD:Test@123456}")
    private String dbPassword;
    
    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        String jdbcUrl = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true",
                dbHost, dbPort, dbName);
        
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        config.setMaximumPoolSize(50);
        config.setMinimumIdle(10);
        config.setConnectionTimeout(20000);
        config.setLeakDetectionThreshold(60000);
        
        log.info("🔷 Configuring SQL Server database");
        log.info("🔷 SQL Server URL: {}", jdbcUrl);
        
        return new HikariDataSource(config);
    }
}
