package com.bank.esps.domain.cdm.product;

/**
 * Economic payout on a contractual product. Aligns with CDM {@code Payout}.
 */
public enum PayoutType {
    PERFORMANCE,
    INTEREST_RATE,
    SETTLEMENT,
    ASSET,
    COMMODITY,
    CREDIT_DEFAULT,
    OPTION,
    FIXED_PRICE
}
