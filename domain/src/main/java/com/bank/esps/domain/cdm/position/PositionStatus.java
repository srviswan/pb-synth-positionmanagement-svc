package com.bank.esps.domain.cdm.position;

/**
 * Lifecycle status of a position. Aligns with CDM
 * {@code cdm.event.position.PositionStatusEnum}.
 */
public enum PositionStatus {
    EXECUTED,
    FORMED,
    SETTLED,
    CANCELLED,
    CLOSED
}
