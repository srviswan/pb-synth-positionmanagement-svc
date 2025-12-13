package com.bank.esps.domain.enums;

/**
 * Reconciliation status for position snapshots
 */
public enum ReconciliationStatus {
    RECONCILED,   // Final, correct position
    PROVISIONAL,  // Temporary, may be corrected (backdated trades)
    PENDING       // Awaiting coldpath processing
}
