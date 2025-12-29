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
        // First, ensure database exists by connecting to master
        ensureDatabaseExists();
        
        // Wait a moment for database to be ready
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
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
    
    private void ensureDatabaseExists() {
        try {
            String masterUrl = String.format("jdbc:sqlserver://%s:%s;databaseName=master;encrypt=true;trustServerCertificate=true",
                    dbHost, dbPort);
            
            log.info("🔷 Ensuring database '{}' exists...", dbName);
            
            HikariConfig masterConfig = new HikariConfig();
            masterConfig.setJdbcUrl(masterUrl);
            masterConfig.setUsername(dbUsername);
            masterConfig.setPassword(dbPassword);
            masterConfig.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            masterConfig.setMaximumPoolSize(1);
            masterConfig.setConnectionTimeout(10000);
            masterConfig.setInitializationFailTimeout(-1); // Don't fail if can't connect immediately
            
            try (HikariDataSource masterDs = new HikariDataSource(masterConfig);
                 java.sql.Connection conn = masterDs.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {
                
                // Check if database exists
                String checkSql = String.format("SELECT COUNT(*) FROM sys.databases WHERE name = '%s'", dbName);
                try (java.sql.ResultSet rs = stmt.executeQuery(checkSql)) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        // Database doesn't exist, create it
                        String createSql = String.format("CREATE DATABASE %s", dbName);
                        stmt.execute(createSql);
                        log.info("🔷 Created database '{}'", dbName);
                    } else {
                        log.info("🔷 Database '{}' already exists", dbName);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not ensure database exists (it may already exist or will be created by Flyway): {}", e.getMessage());
        }
    }
}
