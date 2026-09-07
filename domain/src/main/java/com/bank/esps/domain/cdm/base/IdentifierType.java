package com.bank.esps.domain.cdm.base;

/**
 * Classification of an identifier. Aligns with CDM
 * {@code TradeIdentifierTypeEnum} / position identifier class, plus ESPS
 * internal keys.
 */
public enum IdentifierType {
    UTI,
    USI,
    INTERNAL,
    POSITION_KEY,
    TRADE,
    LOT,
    CONTRACT
}
