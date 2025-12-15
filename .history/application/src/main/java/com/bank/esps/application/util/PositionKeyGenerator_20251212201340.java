package com.bank.esps.application.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for generating deterministic position keys
 * 
 * Position key is a hash of: Account + Instrument + Currency
 * This ensures:
 * - Same account/instrument/currency combination always produces the same position_key
 * - Deterministic partitioning (same position always goes to same partition)
 * - No collisions (with proper hash algorithm)
 */
@Component
public class PositionKeyGenerator {
    
    private static final Logger log = LoggerFactory.getLogger(PositionKeyGenerator.class);
    
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int KEY_LENGTH = 64; // Hex string length (256 bits = 64 hex chars)
    
    /**
     * Generate deterministic position key from account, instrument, and currency
     * 
     * @param account Account identifier
     * @param instrument Instrument identifier (e.g., security symbol)
     * @param currency Currency code (e.g., USD, EUR)
     * @return Deterministic position key (SHA-256 hash as hex string)
     */
    public String generatePositionKey(String account, String instrument, String currency) {
        if (account == null || account.isEmpty()) {
            throw new IllegalArgumentException("Account cannot be null or empty");
        }
        if (instrument == null || instrument.isEmpty()) {
            throw new IllegalArgumentException("Instrument cannot be null or empty");
        }
        if (currency == null || currency.isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be null or empty");
        }
        
        // Normalize inputs (trim, uppercase for consistency)
        String normalizedAccount = account.trim().toUpperCase();
        String normalizedInstrument = instrument.trim().toUpperCase();
        String normalizedCurrency = currency.trim().toUpperCase();
        
        // Create deterministic input string
        // Format: "ACCOUNT|INSTRUMENT|CURRENCY" (pipe separator for clarity)
        String input = String.format("%s|%s|%s", normalizedAccount, normalizedInstrument, normalizedCurrency);
        
        try {
            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String positionKey = hexString.toString();
            log.debug("Generated position key: {} from account={}, instrument={}, currency={}", 
                    positionKey, normalizedAccount, normalizedInstrument, normalizedCurrency);
            
            return positionKey;
            
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Hash algorithm not available", e);
        }
    }
    
    /**
     * Generate position key from a composite key string
     * Useful when the composite key is already formatted
     * 
     * @param compositeKey Format: "ACCOUNT|INSTRUMENT|CURRENCY"
     * @return Position key
     */
    public String generatePositionKeyFromComposite(String compositeKey) {
        if (compositeKey == null || compositeKey.isEmpty()) {
            throw new IllegalArgumentException("Composite key cannot be null or empty");
        }
        
        String[] parts = compositeKey.split("\\|");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                String.format("Invalid composite key format. Expected 'ACCOUNT|INSTRUMENT|CURRENCY', got: %s", compositeKey));
        }
        
        return generatePositionKey(parts[0], parts[1], parts[2]);
    }
    
    /**
     * Parse position key components (reverse lookup - not recommended for production)
     * Note: This is a one-way hash, so we cannot reverse it.
     * This method is for documentation/testing purposes only.
     * 
     * @param positionKey The position key to parse
     * @return null (cannot reverse hash)
     */
    @Deprecated
    public String[] parsePositionKey(String positionKey) {
        // Cannot reverse hash - this is intentional for security
        log.warn("Cannot parse position key - hash is one-way. Position key: {}", positionKey);
        return null;
    }
    
    /**
     * Validate position key format
     * 
     * @param positionKey Position key to validate
     * @return true if valid format (64 hex characters)
     */
    public boolean isValidPositionKeyFormat(String positionKey) {
        if (positionKey == null || positionKey.isEmpty()) {
            return false;
        }
        
        // Should be 64 hex characters (SHA-256 produces 256 bits = 64 hex chars)
        if (positionKey.length() != KEY_LENGTH) {
            return false;
        }
        
        // Should only contain hex characters (0-9, a-f, A-F)
        return positionKey.matches("^[0-9a-fA-F]{64}$");
    }
    
    /**
     * Get the expected partition number for a position key
     * This matches the database partitioning logic
     * 
     * For PostgreSQL: Uses simple hash code modulo (approximates PostgreSQL's hash function)
     * For SQL Server: SQL Server computes partition_hash using HASHBYTES('SHA2_256', ...) internally
     * 
     * @param positionKey Position key
     * @param partitionCount Number of partitions (default: 16)
     * @return Partition number (0 to partitionCount-1)
     */
    public int getPartitionNumber(String positionKey, int partitionCount) {
        if (positionKey == null || positionKey.isEmpty()) {
            throw new IllegalArgumentException("Position key cannot be null or empty");
        }
        
        // Use simple hash code modulo (approximates PostgreSQL hash partitioning behavior)
        // Note: This is a simplified version. 
        // - PostgreSQL uses its own hash function (hashtext())
        // - SQL Server uses HASHBYTES('SHA2_256', ...) in computed column
        // For exact alignment, you'd need to use the same hash function as the database.
        // However, since we use modulo, the distribution will be even regardless.
        int hashCode = positionKey.hashCode();
        int partition = Math.abs(hashCode) % partitionCount;
        
        log.debug("Position key {} maps to partition {} (approximate)", positionKey, partition);
        return partition;
    }
    
    /**
     * Get partition number for SQL Server (using SHA-256 hash to approximate HASHBYTES)
     * 
     * Note: SQL Server's HASHBYTES('SHA2_256', ...) may produce different results than
     * Java's SHA-256 due to implementation differences. For exact results, let SQL Server
     * compute the partition_hash using a computed column.
     * 
     * @param positionKey Position key
     * @param partitionCount Number of partitions (default: 16)
     * @return Partition number (0 to partitionCount-1)
     */
    public int getPartitionNumberForSQLServer(String positionKey, int partitionCount) {
        if (positionKey == null || positionKey.isEmpty()) {
            throw new IllegalArgumentException("Position key cannot be null or empty");
        }
        
        try {
            // Use SHA-256 to approximate SQL Server's HASHBYTES('SHA2_256', ...)
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(positionKey.getBytes(StandardCharsets.UTF_8));
            
            // Convert first 4 bytes to integer (SQL Server HASHBYTES returns VARBINARY)
            // This approximates SQL Server's hash value
            int hashValue = ((hashBytes[0] & 0xFF) << 24) |
                           ((hashBytes[1] & 0xFF) << 16) |
                           ((hashBytes[2] & 0xFF) << 8) |
                           (hashBytes[3] & 0xFF);
            
            int partition = Math.abs(hashValue) % partitionCount;
            log.debug("Position key {} maps to SQL Server partition {} (approximate)", positionKey, partition);
            return partition;
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Hash algorithm not available", e);
        }
    }
    
    /**
     * Get partition number using default partition count (16)
     */
    public int getPartitionNumber(String positionKey) {
        return getPartitionNumber(positionKey, 16);
    }
}
