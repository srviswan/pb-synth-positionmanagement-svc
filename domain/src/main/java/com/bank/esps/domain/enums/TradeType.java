package com.bank.esps.domain.enums;

/**
 * Trade type classification
 */
public enum TradeType {
    NEW_TRADE,   // New position creation
    INCREASE,    // Increase existing position
    DECREASE,    // Decrease existing position
    PARTIAL_TERM, // Partial termination
    FULL_TERM    // Full termination
}
