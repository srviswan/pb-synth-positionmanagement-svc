package com.bank.esps.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
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
    public DataSource dataSource() {
        // Get database type from environment or app config
        String dbType = System.getenv("DB_TYPE");
        if (dbType == null || dbType.isEmpty()) {
            dbType = databaseType;
        }
        
        // Get connection details
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String database = System.getenv().getOrDefault("DB_NAME", "equity_swap_db");
        String username = System.getenv().getOrDefault("DB_USERNAME", 
                "sqlserver".equalsIgnoreCase(dbType) ? "SA" : "postgres");
        String password = System.getenv().getOrDefault("DB_PASSWORD", 
                "sqlserver".equalsIgnoreCase(dbType) ? "Test@123456" : "postgres");
        String url = System.getenv("DB_URL");
        
        DataSourceBuilder<?> builder = DataSourceBuilder.create();
        
        // Set default values based on database type
        if ("sqlserver".equalsIgnoreCase(dbType)) {
            log.info("🔷 Configuring MS SQL Server database");
            
            String port = System.getenv().getOrDefault("DB_PORT", "1433");
            if (url == null || url.isEmpty()) {
                // First, try to create database if it doesn't exist
                try {
                    String masterUrl = String.format("jdbc:sqlserver://%s:%s;databaseName=master;encrypt=true;trustServerCertificate=true", 
                            host, port);
                    try (java.sql.Connection conn = java.sql.DriverManager.getConnection(masterUrl, username, password);
                         java.sql.Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate(String.format(
                            "IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = '%s') CREATE DATABASE [%s]",
                            database, database));
                        log.info("🔷 Database '{}' created or already exists", database);
                    }
                } catch (Exception e) {
                    log.warn("🔷 Could not create database automatically: {}. Will try to connect anyway.", e.getMessage());
                }
                
                url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true", 
                        host, port, database);
            }
            builder.driverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            log.info("🔷 MS SQL Server URL: {}", url);
        } else {
            log.info("🐘 Configuring PostgreSQL database");
            
            String port = System.getenv().getOrDefault("DB_PORT", "5432");
            if (url == null || url.isEmpty()) {
                url = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);
            }
            builder.driverClassName("org.postgresql.Driver");
            log.info("🐘 PostgreSQL URL: {}", url);
        }
        
        builder.url(url);
        builder.username(username);
        builder.password(password);
        
        return builder.build();
    }
}
