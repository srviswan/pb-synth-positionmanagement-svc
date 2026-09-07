package com.bank.esps.domain.cdm.event;

/**
 * Intent of a position lifecycle event. Aligns with CDM
 * {@code PositionEventIntentEnum}.
 */
public enum PositionEventIntent {
    POSITION_CREATION,
    INCREASE,
    DECREASE,
    TRANSFER,
    OPTION_EXERCISE,
    VALUATION,
    CORPORATE_ACTION_ADJUSTMENT
}
