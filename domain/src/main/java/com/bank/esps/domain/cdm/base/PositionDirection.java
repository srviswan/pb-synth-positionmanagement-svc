package com.bank.esps.domain.cdm.base;

/**
 * Long / short direction of a position. ESPS extension used in the
 * position key so a long and a short in the same instrument do not share
 * a {@code CounterpartyPosition}.
 */
public enum PositionDirection {
    LONG,
    SHORT;

    public PositionDirection opposite() {
        return this == LONG ? SHORT : LONG;
    }
}
