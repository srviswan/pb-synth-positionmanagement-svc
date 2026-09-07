package com.bank.esps.domain.enums;

/**
 * Tax-lot allocation method used when decreasing a position.
 * ESPS extension; CDM TradeLot does not encode allocation policy.
 */
public enum TaxLotMethod {
    FIFO,
    LIFO,
    HIFO
}
