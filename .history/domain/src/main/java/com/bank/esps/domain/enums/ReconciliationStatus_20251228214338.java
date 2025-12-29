package com.bank.esps.domain.enums;

/**
 * Reconciliation status for position snapshots
 */
public enum ReconciliationStatus {
    RECONCILED,    // Position is fully reconciled
    PROVISIONAL,   // Provisional position (backdated trade, awaiting coldpath)
    PENDING        // Pending reconciliation
}
