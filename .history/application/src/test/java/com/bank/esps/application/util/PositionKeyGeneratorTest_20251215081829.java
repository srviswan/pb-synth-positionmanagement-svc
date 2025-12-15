package com.bank.esps.application.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PositionKeyGenerator
 */
class PositionKeyGeneratorTest {
    
    private PositionKeyGenerator generator;
    
    @BeforeEach
    void setUp() {
        generator = new PositionKeyGenerator();
    }
    
    @Test
    void testGeneratePositionKey_Deterministic() {
        String key1 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        String key2 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        
        // Same inputs should produce same key
        assertEquals(key1, key2);
        assertEquals(64, key1.length()); // SHA-256 produces 64 hex chars
    }
    
    @Test
    void testGeneratePositionKey_CaseInsensitive() {
        String key1 = generator.generatePositionKey("acc001", "aapl", "usd");
        String key2 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        
        // Should be case-insensitive (normalized to uppercase)
        assertEquals(key1, key2);
    }
    
    @Test
    void testGeneratePositionKey_WhitespaceHandling() {
        String key1 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        String key2 = generator.generatePositionKey(" ACC001 ", " AAPL ", " USD ");
        
        // Whitespace should be trimmed
        assertEquals(key1, key2);
    }
    
    @Test
    void testGeneratePositionKey_DifferentInputs() {
        String key1 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        String key2 = generator.generatePositionKey("ACC002", "AAPL", "USD");
        String key3 = generator.generatePositionKey("ACC001", "MSFT", "USD");
        String key4 = generator.generatePositionKey("ACC001", "AAPL", "EUR");
        
        // Different inputs should produce different keys
        assertNotEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertNotEquals(key1, key4);
    }
    
    @Test
    void testGeneratePositionKey_Format() {
        String key = generator.generatePositionKey("ACC001", "AAPL", "USD");
        
        // Should be 64 hex characters
        assertEquals(64, key.length());
        assertTrue(key.matches("^[0-9a-f]{64}$"));
    }
    
    @Test
    void testGeneratePositionKey_NullAccount() {
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generatePositionKey(null, "AAPL", "USD");
        });
    }
    
    @Test
    void testGeneratePositionKey_EmptyInstrument() {
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generatePositionKey("ACC001", "", "USD");
        });
    }
    
    @Test
    void testGeneratePositionKeyFromComposite() {
        String key1 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        String key2 = generator.generatePositionKeyFromComposite("ACC001|AAPL|USD");
        
        assertEquals(key1, key2);
    }
    
    @Test
    void testGeneratePositionKeyFromComposite_InvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generatePositionKeyFromComposite("INVALID");
        });
    }
    
    @Test
    void testIsValidPositionKeyFormat() {
        String validKey = generator.generatePositionKey("ACC001", "AAPL", "USD");
        
        assertTrue(generator.isValidPositionKeyFormat(validKey));
        assertFalse(generator.isValidPositionKeyFormat("invalid"));
        assertFalse(generator.isValidPositionKeyFormat("123")); // Too short
        assertFalse(generator.isValidPositionKeyFormat(null));
        assertFalse(generator.isValidPositionKeyFormat(""));
    }
    
    @Test
    void testGetPartitionNumber() {
        String key1 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        String key2 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        
        // Same key should map to same partition
        int partition1 = generator.getPartitionNumber(key1, 16);
        int partition2 = generator.getPartitionNumber(key2, 16);
        
        assertEquals(partition1, partition2);
        assertTrue(partition1 >= 0 && partition1 < 16);
    }
    
    @Test
    void testGetPartitionNumber_DifferentKeys() {
        String key1 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        String key2 = generator.generatePositionKey("ACC002", "AAPL", "USD");
        
        int partition1 = generator.getPartitionNumber(key1, 16);
        int partition2 = generator.getPartitionNumber(key2, 16);
        
        // Different keys may map to same or different partitions
        // Just verify they're in valid range
        assertTrue(partition1 >= 0 && partition1 < 16);
        assertTrue(partition2 >= 0 && partition2 < 16);
    }
    
    @Test
    void testGetPartitionNumber_DefaultPartitionCount() {
        String key = generator.generatePositionKey("ACC001", "AAPL", "USD");
        
        int partition = generator.getPartitionNumber(key);
        
        assertTrue(partition >= 0 && partition < 16);
    }
    
    @Test
    void testGetPartitionNumberForSQLServer() {
        String key1 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        String key2 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        
        // Same key should map to same partition
        int partition1 = generator.getPartitionNumberForSQLServer(key1, 16);
        int partition2 = generator.getPartitionNumberForSQLServer(key2, 16);
        
        assertEquals(partition1, partition2);
        assertTrue(partition1 >= 0 && partition1 < 16);
    }
    
    @Test
    void testGetPartitionNumberForSQLServer_DifferentKeys() {
        String key1 = generator.generatePositionKey("ACC001", "AAPL", "USD");
        String key2 = generator.generatePositionKey("ACC002", "AAPL", "USD");
        
        int partition1 = generator.getPartitionNumberForSQLServer(key1, 16);
        int partition2 = generator.getPartitionNumberForSQLServer(key2, 16);
        
        // Different keys may map to same or different partitions
        // Just verify they're in valid range
        assertTrue(partition1 >= 0 && partition1 < 16);
        assertTrue(partition2 >= 0 && partition2 < 16);
    }
    
    @Test
    void testGeneratePositionKeyWithDirection_Long() {
        String keyLong = generator.generatePositionKey("ACC001", "AAPL", "USD", false);
        String keyLong2 = generator.generatePositionKey("ACC001", "AAPL", "USD", false);
        
        // Same inputs should produce same key
        assertEquals(keyLong, keyLong2);
        assertEquals(64, keyLong.length());
    }
    
    @Test
    void testGeneratePositionKeyWithDirection_Short() {
        String keyShort = generator.generatePositionKey("ACC001", "AAPL", "USD", true);
        String keyShort2 = generator.generatePositionKey("ACC001", "AAPL", "USD", true);
        
        // Same inputs should produce same key
        assertEquals(keyShort, keyShort2);
        assertEquals(64, keyShort.length());
    }
    
    @Test
    void testGeneratePositionKeyWithDirection_LongVsShort() {
        String keyLong = generator.generatePositionKey("ACC001", "AAPL", "USD", false);
        String keyShort = generator.generatePositionKey("ACC001", "AAPL", "USD", true);
        
        // Long and short should produce different keys
        assertNotEquals(keyLong, keyShort);
    }
    
    @Test
    void testGeneratePositionKeyWithDirection_DefaultIsLong() {
        String keyDefault = generator.generatePositionKey("ACC001", "AAPL", "USD");
        String keyLong = generator.generatePositionKey("ACC001", "AAPL", "USD", false);
        
        // Default (no direction) should be same as LONG
        assertEquals(keyDefault, keyLong);
    }
    
    @Test
    void testGeneratePositionKeyForOppositeDirection() {
        String keyLong = generator.generatePositionKey("ACC001", "AAPL", "USD", false);
        String keyShort = generator.generatePositionKeyForOppositeDirection("ACC001", "AAPL", "USD", false);
        
        // Opposite direction should produce different key
        assertNotEquals(keyLong, keyShort);
        assertEquals(keyShort, generator.generatePositionKey("ACC001", "AAPL", "USD", true));
    }
}
