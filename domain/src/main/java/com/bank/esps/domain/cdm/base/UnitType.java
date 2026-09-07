package com.bank.esps.domain.cdm.base;

/**
 * Unit of a quantity measure. Aligns with CDM {@code UnitType} (financial
 * unit / currency) used on {@code Quantity} and {@code Price}.
 */
public enum UnitType {
    SHARES,
    CONTRACTS,
    CURRENCY
}
