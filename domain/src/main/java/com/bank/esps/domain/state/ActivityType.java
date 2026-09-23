package com.bank.esps.domain.state;

/**
 * A fact applied directly to position state.
 * OPEN creates a lot. CLOSE reduces lots. RESET replaces the open price.
 * STOCK_SPLIT scales open quantity and price. DIVIDEND pays cash and reduces the open price.
 */
public enum ActivityType {
    OPEN,
    CLOSE,
    RESET,
    STOCK_SPLIT,
    DIVIDEND
}
