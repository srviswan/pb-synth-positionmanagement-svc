package com.bank.esps.domain.enums;

/**
 * Trade sequence status based on effective date comparison with snapshot date
 */
public enum TradeSequenceStatus {
    CURRENT_DATED,  // Effective date >= latest snapshot date
    FORWARD_DATED,  // Effective date > current date
    BACKDATED       // Effective date < latest snapshot date
}
