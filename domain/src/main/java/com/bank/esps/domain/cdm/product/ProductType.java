package com.bank.esps.domain.cdm.product;

/**
 * Top-level contractual product. Aligns with CDM product qualification
 * ({@code EquitySwap}, CFD-style performance products, options, forwards).
 * New product families are added here without changing basket persistence.
 */
public enum ProductType {
    SWAP,
    CFD,
    FORWARD,
    OPTION
}
