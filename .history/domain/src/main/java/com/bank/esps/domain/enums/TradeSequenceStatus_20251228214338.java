package com.bank.esps.domain.enums;

/**
 * Trade sequence status based on effective date
 */
public enum TradeSequenceStatus {
    CURRENT_DATED,  // Trade effective date >= latest snapshot date
    FORWARD_DATED,  // Trade effective date > current date
    BACKDATED       // Trade effective date < latest snapshot date
}
