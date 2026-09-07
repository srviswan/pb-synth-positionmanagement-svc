package com.bank.esps.domain.cdm.event;

/**
 * Direction of a quantity-change primitive. Aligns with CDM
 * {@code QuantityChangeDirectionEnum}. Quantity on the instruction is always
 * a positive number; this enum carries the sign.
 */
public enum QuantityChangeDirection {
    INCREASE,
    DECREASE,
    REPLACE
}
