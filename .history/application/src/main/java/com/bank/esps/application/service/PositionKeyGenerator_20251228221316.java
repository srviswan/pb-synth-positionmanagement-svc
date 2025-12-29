package com.bank.esps.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates position keys with direction (LONG/SHORT) included
 * 
 * Key insight: When direction changes (long ↔ short), create a NEW position_key
 * This simplifies logic - each position_key has a single direction
 */
@Component
public class PositionKeyGenerator {
    
    private static final Logger log = LoggerFactory.getLogger(PositionKeyGenerator.class);
    
    public enum Direction {
        LONG,   // Positive quantities
        SHORT   // Negative quantities
    }
    
    /**
     * Generate position key with direction included in hash
     * Format: Hash(Account + Instrument + Currency + Direction)
     * 
     * @param account Account identifier
     * @param instrument Instrument identifier
     * @param currency Currency code
     * @param direction LONG or SHORT
     * @return Position key with direction encoded
     */
    public String generatePositionKey(String account, String instrument, String currency, Direction direction) {
        // Normalize inputs
        String normalizedAccount = (account != null ? account.trim().toUpperCase() : "");
        String normalizedInstrument = (instrument != null ? instrument.trim().toUpperCase() : "");
        String normalizedCurrency = (currency != null ? currency.trim().toUpperCase() : "");
        String normalizedDirection = direction.name().toUpperCase();
        
        // Create hash input: Account|Instrument|Currency|Direction
        String input = String.format("%s|%s|%s|%s", 
                normalizedAccount, 
                normalizedInstrument, 
                normalizedCurrency,
                normalizedDirection);
        
        // Generate hash
        String hash = hashToHex(input);
        
        log.debug("Generated position key: {} for account={}, instrument={}, currency={}, direction={}", 
                hash, normalizedAccount, normalizedInstrument, normalizedCurrency, normalizedDirection);
        
        return hash;
    }
    
    /**
     * Generate position key from trade event, determining direction from quantity sign
     * 
     * @param tradeEvent Trade event
     * @return Position key with direction
     */
    public String generatePositionKeyFromTrade(com.bank.esps.domain.event.TradeEvent tradeEvent) {
        // Determine direction from quantity sign
        Direction direction = determineDirection(tradeEvent.getQuantity());
        return generatePositionKey(
                tradeEvent.getAccount(),
                tradeEvent.getInstrument(),
                tradeEvent.getCurrency(),
                direction);
    }
    
    /**
     * Determine direction from quantity sign
     * Positive quantity = LONG, Negative quantity = SHORT
     */
    public Direction determineDirection(java.math.BigDecimal quantity) {
        if (quantity == null) {
            return Direction.LONG; // Default to LONG
        }
        return quantity.compareTo(java.math.BigDecimal.ZERO) >= 0 ? Direction.LONG : Direction.SHORT;
    }
    
    /**
     * Extract direction from position key (if stored separately)
     * For now, we'll determine from position state
     */
    public Direction extractDirection(com.bank.esps.domain.model.PositionState state) {
        if (state == null || state.getOpenLots() == null || state.getOpenLots().isEmpty()) {
            return Direction.LONG; // Default
        }
        
        // Determine from total quantity
        java.math.BigDecimal totalQty = state.getTotalQty();
        return determineDirection(totalQty);
    }
    
    /**
     * Generate position key for opposite direction
     * Used when sign change is detected
     */
    public String generateOppositeDirectionKey(String account, String instrument, String currency, Direction currentDirection) {
        Direction opposite = (currentDirection == Direction.LONG) ? Direction.SHORT : Direction.LONG;
        return generatePositionKey(account, instrument, currency, opposite);
    }
    
    /**
     * Hash string to hex
     */
    private String hashToHex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string (first 16 chars for shorter key)
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 8; i++) { // Use first 8 bytes = 16 hex chars
                String hex = Integer.toHexString(0xff & hashBytes[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback: use simple hash code
            return Integer.toHexString(input.hashCode());
        }
    }
}
