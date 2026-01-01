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
        
        HikariConfig config = new HikariConfig();
        
        String jdbcUrl = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true;loginTimeout=30",
                dbHost, dbPort, dbName);
        
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        config.setMaximumPoolSize(50);
        config.setMinimumIdle(10);
        config.setConnectionTimeout(30000); // Increased to 30 seconds
        config.setInitializationFailTimeout(60000); // Wait up to 60 seconds for initial connection
        config.setLeakDetectionThreshold(60000);
        config.setValidationTimeout(5000);
        config.setConnectionTestQuery("SELECT 1");
        
        log.info("🔷 Configuring SQL Server database");
        log.info("🔷 SQL Server URL: {}", jdbcUrl);
        log.info("🔷 Connection timeout: {}ms, Initialization timeout: {}ms", 
                config.getConnectionTimeout(), config.getInitializationFailTimeout());
        
        HikariDataSource dataSource = new HikariDataSource(config);
        
        // Test connection with retry logic
        testConnectionWithRetry(dataSource, 3, 2000);
        
        return dataSource;
    }
    
    private void testConnectionWithRetry(HikariDataSource dataSource, int maxRetries, long delayMs) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("🔷 Testing database connection (attempt {}/{})...", attempt, maxRetries);
                try (java.sql.Connection conn = dataSource.getConnection()) {
                    log.info("🔷 ✅ Database connection successful!");
                    return;
                }
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("🔷 ❌ Failed to connect to database after {} attempts", maxRetries);
                    log.error("🔷 Error: {}", e.getMessage());
                    log.error("🔷 Please ensure SQL Server is running and accessible at {}:{}", dbHost, dbPort);
                    log.error("🔷 You can start SQL Server using: docker-compose up -d sqlserver");
                    // Don't throw - let HikariCP handle connection retries
                    log.warn("🔷 Continuing anyway - HikariCP will retry connections as needed");
                } else {
                    log.warn("🔷 Connection attempt {} failed: {}. Retrying in {}ms...", 
                            attempt, e.getMessage(), delayMs);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("🔷 Interrupted while waiting for retry");
                        break;
                    }
                }
            }
        }
    }
    
    private void ensureDatabaseExists() {
        try {
            String masterUrl = String.format("jdbc:sqlserver://%s:%s;databaseName=master;encrypt=true;trustServerCertificate=true;loginTimeout=10",
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
            masterConfig.setConnectionTestQuery("SELECT 1");
            
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
            // Check if the root cause is a socket/network error
            Throwable cause = e;
            while (cause != null && !(cause instanceof java.net.SocketException) && 
                   !(cause instanceof java.net.ConnectException)) {
                cause = cause.getCause();
            }
            
            if (cause instanceof java.net.SocketException || cause instanceof java.net.ConnectException) {
                log.warn("🔷 Could not connect to SQL Server at {}:{} - {}", dbHost, dbPort, cause.getMessage());
                log.warn("🔷 This is expected if SQL Server is not running. The application will retry connections.");
                log.warn("🔷 To start SQL Server: docker-compose up -d sqlserver");
            } else {
                log.warn("🔷 Could not ensure database exists (it may already exist or will be created by Flyway): {}", e.getMessage());
                if (e.getCause() != null) {
                    log.warn("🔷 Root cause: {}", e.getCause().getMessage());
                }
            }
        }
    }
}
