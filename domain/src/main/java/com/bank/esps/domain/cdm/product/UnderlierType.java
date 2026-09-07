package com.bank.esps.domain.cdm.product;

/**
 * Observable / asset used as a product underlier. Aligns with CDM
 * {@code Observable} choice: Asset, Index, Basket.
 */
public enum UnderlierType {
    EQUITY,
    INDEX,
    FX,
    INTEREST_RATE,
    COMMODITY,
    CREDIT,
    BASKET
}
