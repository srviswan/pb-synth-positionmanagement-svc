package com.bank.esps.domain.enums;

/**
 * Event types in the event store
 */
public enum EventType {
    NEW_TRADE,
    INCREASE,
    DECREASE,
    RESET,
    CORRECTION,
    POSITION_CLOSED,
    PROVISIONAL_TRADE_APPLIED,
    HISTORICAL_POSITION_CORRECTED
}
