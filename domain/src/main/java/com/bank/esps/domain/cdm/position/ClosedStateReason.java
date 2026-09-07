package com.bank.esps.domain.cdm.position;

/**
 * What closed a position. Aligns with CDM {@code ClosedStateEnum}.
 */
public enum ClosedStateReason {
    ALLOCATED,
    CANCELLED,
    EXERCISED,
    EXPIRED,
    MATURED,
    NOVATED,
    TERMINATED
}
